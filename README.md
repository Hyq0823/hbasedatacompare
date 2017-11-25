# Concurrent scanning of data sources 工具介绍
```
  这是一个并发扫描数据源(hbase,redis)的工具,多线程scan提高效率。
```

## 代码说明
```
    1.concurrentRedisScan：多线程scan redis（每个线程scan一个redis的slave）获取所有的key，只适用于redis3.0以上cluster模式
       redis客户端需要提供获取所有slave节点id，已经在指定节点上scan key的api(lib目录下有我们使用的自己封装的nedis jar包)。
       如果key的数目很多需要在程序启动的时候指定合适的jvm内存大小防止内存溢出。
       scan redis key不属于cpu密集型操作，线程池大小可以设置大一些（cpu核数的两倍）。

    2.hbasedatacompare ：多线程scan HTable,获取HTable所在的每一个region,每个region期一个线程去scan，并发提高效率
       关于线程池的大小设置：根据你线程中的操作是io密集型还是cpu密集型：
       如果是cpu密集型的操作，对cpu的消耗很大，建议线程池设置为cpu总和数的一半，避免将机器资源耗尽。同时注意gc回收策略和运行内存大小的指定。
       如果是io密集型的操作，cpu进程之间的切换开销不是很大，可以把线程池设置为cpu总和数的2倍，根号的利用cpu资源。

```