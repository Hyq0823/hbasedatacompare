package util;

import constant.Constant;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


public class HbaseUtils {

	private static Configuration cfg = null;
	private static Connection connection = null;

	private static final Logger logger = Logger.getLogger(HbaseUtils.class);


	/**
	 * 初始化连接Hbase
	 * @param zkaddr zk的地址
	 */
	public HbaseUtils(String zkaddr){
 	     try {
 	    	 cfg = HBaseConfiguration.create();
 	    	 cfg.set("hbase.zookeeper.quorum", zkaddr);

 	    	//设置hbase连接超时
 	    	 cfg.setInt("hbase.rpc.timeout",20000);
 	    	 cfg.setInt("hbase.client.operation.timeout",30000);
 	    	 cfg.setInt("hbase.client.scanner.timeout.period",100000);

			 connection = ConnectionFactory.createConnection(cfg);
			 logger.info("hbase connect success");
		 }catch(IOException e) {
			 logger.warn(e);
		 }

	}

	public Connection getConnection(){
         return connection;
	}



	/**
	 * 获取指定行指定列的数据
	 * @param tablename 表名
	 * @param rowkey 行键key
	 * @param columnFamily 列族
	 * @param column 列名
	 */
	public String getData(TableName tablename,String rowkey,String columnFamily,String column) {
		String value = "";
		try {
			 Table table = connection.getTable(tablename);
			 Get get = new Get(Bytes.toBytes(rowkey));
			 Result result = table.get(get);
			 result.getRow();
			 byte[] rb = result.getValue(Bytes.toBytes(columnFamily), Bytes.toBytes(column));
			 if(rb != null){
				 value = new String(rb,"UTF-8");
			 }
		} catch (IOException e) {
			logger.warn(e);
		}
		return value;  
	}

	/**
	 * 获取指定的table
	 * @param tablename
	 * @return
	 */
	public Table getTable(TableName tablename) {
		Table table = null;
		try {
			table =  connection.getTable(tablename);
		} catch (IOException e) {
			logger.warn(e);
		}
		return table;
	}

	/**
	 * 表中某个row是否存在
	 * @param tablename
	 * @param rowkey
	 * @return
	 */
	public boolean isRowExist(TableName tablename,String rowkey) {
		try {
			 Table table = connection.getTable(tablename);
			 Get get = new Get(Bytes.toBytes(rowkey));
			 //get.addColumn(Bytes.toBytes("family"),Bytes.toBytes("qualifier"));
			 //get.addFamily(Bytes.toBytes("family"));
			 Result result = table.get(get);
			 byte[] rb = result.getRow();
			 if(rb != null){
				return true;
			 }
			
		} catch (IOException e) {
			logger.warn(e);
		}
		return false;  
	}
	
	/**
	 * 获取表中某个row的result
	 * @param tablename
	 * @param rowkey
	 * @return
	 */
	public Result getResult(TableName tablename,String rowkey) {
		Result result = new Result();
		Table table = null;
		try {
			 table = connection.getTable(tablename);
			 Get get = new Get(Bytes.toBytes(rowkey));
			 result = table.get(get);
			
		} catch (IOException e) {
			logger.error(e);
		}finally {
			if (table != null){
				try {
					table.close();
				} catch (IOException e) {
					logger.error(e);
				}
			}
		}
		return result;  
	}


	public List<Result> getResultList(TableName tablename, List<Result> resultlist) {
		List<Result> results = new LinkedList<Result>();
		List<Get> gets = new LinkedList<Get>();
		try {
			 Table table = connection.getTable(tablename);
			 for(Result result:resultlist){
				 //构造getlist批量查询
				 gets.add(new Get(result.getRow()));
			 }
			//构建返回的结果
			 for(Result result:table.get(gets)){
				 results.add(result);
			 }
			
		} catch (IOException e) {
			logger.warn("found exception:", e);
		}
		return results;  
	}


	public List<Result> getResultList(TableName tablename, List<Result> resultlist,List<byte[]> columns) {
		List<Result> results = new LinkedList<Result>();
		List<Get> gets = new LinkedList<Get>();
		Table table = null;

		try {
			 table = connection.getTable(tablename);
			for(Result result:resultlist){
				//构造getlist批量查询
				Get get = new Get(result.getRow());
				columns.forEach(column->get.addColumn(Constant.BYTE_CF_EC,column));
				gets.add(get);

			}
			//构建返回的结果
			for(Result result:table.get(gets)){
				results.add(result);
			}

		} catch (IOException e) {
			logger.warn("found exception:", e);
		}finally {
			if (table != null){
				try {
					table.close();
				} catch (IOException e) {
					logger.error(e);
				}
			}
		}
		return results;
	}
	
	/**
	 *关闭hbase连接
	 */
	public void closeHbase(){
		try {
			connection.close();
			logger.info("hbase close success");
		} catch (IOException e) {
			logger.warn("found exception:", e);
		}
	}


}
