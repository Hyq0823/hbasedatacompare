package core;

import com.lambdaworks.redis.KeyScanCursor;
import com.lambdaworks.redis.ScanArgs;
import com.newegg.ec.nedis.bootstrap.Nedis;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by lf52 on 2017/11/24.
 * 多线程scan redis（每个线程scan一个redis的slave）
 */
public class concurrentRedisScan {

    public static final ExecutorService executorService =  Executors.newFixedThreadPool(10);
    public static final Log log = LogFactory.getLog(concurrentRedisScan.class);
    public static final String CACHE_PATTERN_KEYS = "itemService_itemPricingByType*";

    /**
     * 获取 slave 中所有的 key
     * @return
     */
    private static int getSlaveRedisKeys () {
        List<Future<List<String>>> list = new LinkedList<>();
        Nedis nedis = new Nedis("nedis-cluster.xml");
        int total = 0;
        try {
            log.info( "get slave of list: " + nedis.getAllSlaveNodes() );
            for(String slaveNode:nedis.getAllSlaveNodes()){
                ScanRedisCallback scanRedisCallback = new ScanRedisCallback(slaveNode);
                Future<List<String>> future = executorService.submit(scanRedisCallback);
                list.add(future);
            }
            for(Future<List<String>> future : list){
                total = total + future.get().size();
            }
            log.info("add scanRedisCallback to executor pool finish!!!!! total keys: " + total );
        }catch ( Exception e ){
            log.error( e );
        }finally {
            nedis.returnResource();
        }
        return total;
    }

    /**
     *  扫描 redis
     */
    static class ScanRedisCallback implements Callable<List<String>> {
        private String currentNodeId;
        public ScanRedisCallback(String currentNodeId){
            this.currentNodeId = currentNodeId;
        }
        @Override
        public List<String> call() throws Exception {
            Nedis nedis = new Nedis("nedis-cluster.xml");
            List<String> keysList = new LinkedList<>();
            try{
                ScanArgs scanArgs = new ScanArgs();
                // 如果是初始化状态那么就扫描cache的key
                scanArgs.match( CACHE_PATTERN_KEYS );
                scanArgs.limit(1000);
                KeyScanCursor<String> scanCursor = nedis.scan(null,scanArgs,currentNodeId);
                keysList.addAll(scanCursor.getKeys());
                while(!scanCursor.isFinished()){
                    scanCursor= nedis.scan(scanCursor,scanArgs,currentNodeId);
                    keysList.addAll(scanCursor.getKeys());
                }
                System.out.println(Thread.currentThread().getName()+" keysList size: " + keysList.size());
            }catch ( Exception e ){
                log.error( e );
            }finally {
                nedis.returnResource();
            }
            return keysList;
        }
    }

    public static void main(String[] args) {
        System.out.println(getSlaveRedisKeys());
    }
}
