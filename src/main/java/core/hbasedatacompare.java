package core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import constant.Constant;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.log4j.Logger;
import util.HbaseUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by lf52 on 2017/10/19.
 * 1.比较两个Hbase集群的数据的一致性
 * 2.多线程scan HTable,获取HTable所在的每一个region,每个region期一个线程去scan，并发提高效率
 *   关于线程池的大小设置：根据你线程中的操作是io密集型还是cpu密集型：
 *     如果是cpu密集型的操作，对cpu的消耗很大，建议线程池设置为cpu总和数的一半，避免将机器资源耗尽。同时注意gc回收策略和运行内存大小的指定。
 *     如果是io密集型的操作，cpu进程之间的切换开销不是很大，可以把线程池设置为cpu总和数的2倍，根号的利用cpu资源。
 *
 * 3.本例子中，我们需要在线程中从被比较的hbase中拉去数据，然后循环比较每个cloumn的值是否一样，对cpu消耗比较大，说与cpu密集型操作。经测试当线程池设置过大时机器的 load average非常高。
 */
public class hbasedatacompare {

    private static final Logger logger = Logger.getLogger(hbasedatacompare.class);

    private String tablename;

    private static Connection firsthbasegetConnection = null;
    private static HbaseUtils secondhbaseutil = null;
    private static ExecutorService threadPool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors()/2, Runtime.getRuntime().availableProcessors()/2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(Runtime.getRuntime().availableProcessors() * 2, true),
            new ThreadFactoryBuilder().setNameFormat("Hbase Scan Pool-thread-%d").build(),
            new ThreadPoolExecutor.AbortPolicy());
    private LinkedList<String> compareList  = new LinkedList<>();
    private List<byte[]> columnList = new ArrayList<>(20);

    static int alltotal = 0;
    static int allnotmatch = 0;



    public hbasedatacompare(String tablename) {
        this.tablename = tablename;
    }

    public void run() {

        try {
            /*firsthbasegetConnection = new HbaseUtils("e11echdp03.mercury.corp").getConnection();
            secondhbaseutil = new HbaseUtils("e11hdp01.mercury.corp");*/
            firsthbasegetConnection = new HbaseUtils("ssecbigdata08").getConnection();
            secondhbaseutil = new HbaseUtils("ssspark03");
            initCompareList(tablename);

            List<HRegionLocation> hRegionLocationList =  firsthbasegetConnection.getRegionLocator(TableName.valueOf(tablename)).getAllRegionLocations();
            List<Pair<byte[],byte[]>> pairList = new LinkedList<>();

            for(HRegionLocation hRegionLocation:hRegionLocationList){
                HRegionInfo region = hRegionLocation.getRegionInfo();
                byte[] startKeys = region.getStartKey();
                byte[] endKeys = region.getEndKey();
                pairList.add(new Pair<>(startKeys,endKeys));
            }
            logger.info("region size : " + pairList.size());

            List<Future<String>> futureList = new ArrayList<>();
            for(Pair<byte[], byte[]> pair: pairList){
                Future<String> futureResSet = threadPool.submit( new HBaseScannerCallBack(pair,tablename,firsthbasegetConnection));
                futureList.add(futureResSet);
            }

            for(Future<String> future : futureList){
                String data =  future.get();
                allnotmatch = allnotmatch+ Integer.parseInt(data.split(",")[0]);
                alltotal = alltotal+ Integer.parseInt(data.split(",")[1]);
            }


        }catch (Exception e){
            logger.error(e);
        }finally {
            if (firsthbasegetConnection!=null){
                try {
                    firsthbasegetConnection.close();
                } catch (IOException e) {
                    logger.error(e);
                }
            }
        }
    }

    // 表数据比对逻辑
   private int compareHbase1AndHbase(String tableName,List<Result> results1,LinkedList<String> comparelist, List<byte[]> columnList,String threadname) {
       int notmatch = 0;

        List<Result> results2 = secondhbaseutil.getResultList(TableName.valueOf(tableName), results1,columnList);
        for (Result sssecresult_temp : results2) {
            if(sssecresult_temp.getRow() == null){
                notmatch++;
                continue;
            }
            String sssecrow = Bytes.toString(sssecresult_temp.getRow());

                for (Result sssparkresult_temp : results1) {

                    if(sssecrow.equals(Bytes.toString(sssparkresult_temp.getRow()))){

                        //组装map,value，只比指定的key
                        Map<String,String> spcellmap = new HashMap();
                        Map<String,String> secellmap = new HashMap();
                        for (Cell secell : sssecresult_temp.listCells()) {
                            for (Cell spcell : sssparkresult_temp.listCells()) {
                                String spkey = Bytes.toString(CellUtil.cloneQualifier(spcell));
                                String spvalue  = Bytes.toString(CellUtil.cloneValue(spcell)).trim();
                                if(comparelist.contains(spkey)){
                                    spcellmap.put(Bytes.toString(CellUtil.cloneQualifier(spcell)), spvalue);
                                }

                            }
                            String sekey = Bytes.toString(CellUtil.cloneQualifier(secell));
                            String sevalue  = Bytes.toString(CellUtil.cloneValue(secell)).trim();
                            if(comparelist.contains(sekey) && sevalue!=null ){
                                secellmap.put(Bytes.toString(CellUtil.cloneQualifier(secell)), sevalue);
                            }

                        }

                        boolean ismatch = compare(spcellmap,secellmap);
                        if (!ismatch) {
                            notmatch++;
                            logger.info(threadname + " : not match rowkey======"+sssecrow);
                            break;
                        }

                    }
                }
            }
           return notmatch;

        }

        private boolean compare(Map<String,String> cellValueMap1, Map<String,String> cellValueMap2) {

        if(cellValueMap1.size() == cellValueMap2.size()){

            for(String column : cellValueMap1.keySet()){

                String value1 = cellValueMap1.get(column);
                String value2 = cellValueMap2.get(column);
                if( !value1.equalsIgnoreCase(value2)){
                    return false;
                }
            }

        }
        return true;

    }

    private void initCompareList(String tablename) {
        if (tablename.equals("ecitem:IM_ItemBase")){
            compareList.add("Checked");
            columnList.add(Bytes.toBytes("Checked"));
            compareList.add("DownloadSoftwareFlag");
            columnList.add(Bytes.toBytes("DownloadSoftwareFlag"));
        }else{
            compareList.add("ItemNumber");
            columnList.add(Bytes.toBytes("ItemNumber"));
            compareList.add("CountryCode");
            columnList.add(Bytes.toBytes("CompanyCode"));
            compareList.add("CompanyCode");
            columnList.add(Bytes.toBytes("CompanyCode"));
            compareList.add("ComboReservedQ4S");
            columnList.add(Bytes.toBytes("ComboReservedQ4S"));
            compareList.add("UnitPrice");
            columnList.add(Bytes.toBytes("UnitPrice"));
            compareList.add("PurolatorUSAShippingCharge");
            columnList.add(Bytes.toBytes("PurolatorUSAShippingCharge"));
            compareList.add("Active");
            columnList.add(Bytes.toBytes("Active"));
            compareList.add("WebsiteBlockMark");
            columnList.add(Bytes.toBytes("WebsiteBlockMark"));
            compareList.add("DiscountInstant");
            columnList.add(Bytes.toBytes("DiscountInstant"));
            compareList.add("PriceHideMark");
            columnList.add(Bytes.toBytes("PriceHideMark"));
            compareList.add("MinShippingCharge");
            columnList.add(Bytes.toBytes("MinShippingCharge"));

        }

    }


    class HBaseScannerCallBack implements Callable<String>{
        private Pair<byte[],byte[]> pair;
        private String tableName;
        private Table hbaseTable;
        List<Result> resultlist = new LinkedList<>();

        public HBaseScannerCallBack(Pair<byte[],byte[]> pair,String tableName,Connection connection) throws IOException {
            this.pair = pair;
            this.tableName = tableName;
            this.hbaseTable = connection.getTable(TableName.valueOf(tableName));
        }

        private int total = 0;
        private int notmatch = 0;

        @Override
        public String call() {
            try {
                Scan scan = new Scan();
                scan.setStartRow(pair.getFirst());
                scan.setStopRow(pair.getSecond());
                scan.setCaching(1000);

                //时间戳scan
                /*long endTime = System.currentTimeMillis();
                long startTime = endTime - 1000;
                scan.setTimeRange(startTime, endTime);*/

                columnList.forEach(column->scan.addColumn(Constant.BYTE_CF_EC,column));
                ResultScanner resultScanner = hbaseTable.getScanner(scan);
                Iterator<Result> iterator = resultScanner.iterator();
                String threadname = Thread.currentThread().getName();
                while (iterator.hasNext()) {
                    try {

                        Result result = iterator.next();

                        resultlist.add(result);

                        try{
                            if (resultlist.size() == 1000) {
                                //resultlist达到1000时开始做一次对比操作
                                notmatch = notmatch + compareHbase1AndHbase(tableName,resultlist, compareList, columnList,threadname);
                                total = total + resultlist.size();
                                logger.info(threadname + " total :"+total + " notmatch:" + notmatch);
                                // 清空list
                                resultlist = new LinkedList();
                            }
                        }catch (Exception e){
                            logger.error("hbase batch get and compare 发生异常",e);
                        }


                    }catch (Exception e){
                        logger.error(tableName+" has Exception:",e);
                    }
                }

                if (resultlist.size() >0) {
                    notmatch = notmatch + compareHbase1AndHbase(tableName,resultlist, compareList, columnList,threadname);
                    total = total + resultlist.size();
                    logger.info(threadname + " total :"+total + " notmatch:" + notmatch);
                    // 清空list
                    resultlist = new LinkedList();
                }

            } catch (IOException e) {
                logger.error(e);
            }finally {
                if(hbaseTable!=null){
                    try {
                        hbaseTable.close();
                    } catch (IOException e) {
                        logger.error(e);
                    }
                }
            }
            return notmatch+","+total;
        }
    }

    public static void main(String[] args) {
        //String tablename = args[0];
        String tablename = "ecitem:IM_ItemPrice";
        long starttime = System.currentTimeMillis();
        new hbasedatacompare(tablename).run();
        long endtime = System.currentTimeMillis();
        long time = endtime - starttime;
        logger.info("all compare end ! all total : " + alltotal + ", all notmatch: " + allnotmatch + ", costtime: " +  time/1000 + " second");

    }


}
