package core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lambdaworks.redis.KeyScanCursor;
import com.lambdaworks.redis.ScanArgs;
import com.newegg.ec.nedis.bootstrap.Nedis;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by lf52 on 2017/11/24.
 * 多线程scan redis（每个线程scan一个redis的slave）获取所有的key，只适用于redis3.0以上cluster模式
 *     1.redis客户端需要提供获取所有slave节点id，已经在指定节点上scan key的api。
 *     2.如果key的数目很多需要在程序启动的时候指定合适的jvm内存大小防止内存溢出。
 *     3.scan redis key不属于cpu密集型操作，线程池大小可以设置大一些（cpu核数的两倍）
 */
public class concurrentRedisScan {


    public static final Log log = LogFactory.getLog(concurrentRedisScan.class);

    public static final ExecutorService executorService =  new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors()*2, Runtime.getRuntime().availableProcessors()*2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(Runtime.getRuntime().availableProcessors() * 8, true),
            new ThreadFactoryBuilder().setNameFormat("Redis Scan Pool-thread-%d").build(),
            new ThreadPoolExecutor.AbortPolicy());
    public static final String CACHE_PATTERN_KEYS = "*";

    /**
     * 获取 slave 中所有的 key
     * @return
     */
    private static int getSlaveRedisKeys () {
        List<Future<String>> list = new LinkedList<>();
        Nedis nedis = new Nedis("nedis-cluster.xml");
        int total = 0;
        try {
            log.info( "get slave of list: " + nedis.getAllSlaveNodes() );
            for(String slaveNode:nedis.getAllSlaveNodes()){
                ScanRedisCallback scanRedisCallback = new ScanRedisCallback(slaveNode);
                Future<String> future = executorService.submit(scanRedisCallback);
                list.add(future);
            }
            for(Future<String> future : list){
                total = total + Integer.parseInt(future.get());
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
    static class ScanRedisCallback implements Callable<String> {
        private String currentNodeId;
        public ScanRedisCallback(String currentNodeId){
            this.currentNodeId = currentNodeId;
        }
        public int keysize = 0;
        @Override
        public String call() throws Exception {
            Nedis nedis = new Nedis("nedis-cluster.xml");
            List<byte[]> keysList = new LinkedList<>();
            //List<String> keysList = new LinkedList<>();
            try{
                ScanArgs scanArgs = new ScanArgs();
                // 如果是初始化状态那么就扫描cache的key
                scanArgs.match( CACHE_PATTERN_KEYS );
                scanArgs.limit(1000);
                KeyScanCursor<byte[]> scanCursor = nedis.scan(null,scanArgs,currentNodeId);
                //KeyScanCursor<byte[]> scanCursor = nedis.scan(null,scanArgs,currentNodeId);
                keysList.addAll(scanCursor.getKeys());
                while(!scanCursor.isFinished()){
                    scanCursor= nedis.scan(scanCursor,scanArgs,currentNodeId);
                    keysList.addAll(scanCursor.getKeys());
                    if(keysList.size() >= 1000){
                        keysize = keysize + keysList.size();
                        log.info(Thread.currentThread().getName() + " ======================now size :"+ keysize);
                        keysList = new LinkedList<>();
                    }

                }

                if(keysList.size() > 0){
                    keysize = keysize + keysList.size();
                    log.info(Thread.currentThread().getName() + " ======================total size :"+ keysize);
                }
                log.info(Thread.currentThread().getName() + " keysList size: " + keysize);
            }catch ( Exception e ){
                log.error( e );
            }finally {
                nedis.returnResource();
            }
            return keysize+"";
        }
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int totalkey = getSlaveRedisKeys();
        long end = System.currentTimeMillis();
        long time = end - start;

        System.out.println("total key is : " + totalkey + " cost time : " + time);
    }
}
