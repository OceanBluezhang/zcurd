package com.zcurd.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.Record;
import com.zcurd.common.DBTool;
import com.zcurd.common.DbMetaTool;
import com.zcurd.common.StringUtil;
import com.zcurd.common.ZcurdTool;
import com.zcurd.model.ZcurdField;
import com.zcurd.model.ZcurdHead;
import com.zcurd.vo.ZcurdMeta;

/**
 * 在线表单相关业务
 * @author 钟世云 2016.2.5
 */
public class ZcurdService {
	
	public void add(int headId, Map<String, String[]> paraMap) {
		ZcurdMeta mapmeta = getMetaData(headId);
		ZcurdHead head = mapmeta.getHead();
		List<ZcurdField> addFieldList = mapmeta.getAddFieldList();

		Record record = new Record();
		for (ZcurdField field : addFieldList) {
			String[] paramValues = paraMap.get("model." + field.getStr("field_name"));
			record.set(field.getStr("field_name"), paramValues == null ? null : paramValues[0]);
		}
		Db.use(ZcurdTool.getDbSource(head.getDbSource())).save(head.getStr("table_name"), head.getStr("id_field"), record);
	}
	
	public void update(int headId, int id, Map<String, String[]> paraMap) {
		ZcurdMeta mapmeta = getMetaData(headId);
		ZcurdHead head = mapmeta.getHead();
		List<ZcurdField> updateFieldList = mapmeta.getUpdateFieldList();

		Record record = get(headId, id);
		for (ZcurdField field : updateFieldList) {
			if(field.getInt("is_allow_update") == 1) {
				String[] paramValues = paraMap.get("model." + field.getStr("field_name"));
				record.set(field.getStr("field_name"), paramValues == null ? null : paramValues[0]);
			}
		}
		Db.use(ZcurdTool.getDbSource(head.getDbSource())).update(head.getStr("table_name"), head.getStr("id_field"), record);
	}
	
	public void delete(int headId, Integer[] ids) {
		ZcurdMeta mapmeta = getMetaData(headId);
		ZcurdHead head = mapmeta.getHead();
		
		for (Integer id : ids) {
			Db.use(ZcurdTool.getDbSource(head.getDbSource())).deleteById(head.getStr("table_name"), head.getStr("id_field"), id);
		}
		DbMetaTool.updateMetaData(headId);
	}
	
	
	public Record get(int headId, int id) {
		ZcurdMeta mapmeta = getMetaData(headId);
		ZcurdHead head = mapmeta.getHead();
		
		Record record = Db.use(ZcurdTool.getDbSource(head.getDbSource())).findById(head.getStr("table_name"), head.getStr("id_field"), id);
		return record;
	}
	
	public ZcurdMeta getMetaData(int headId) {
		return DbMetaTool.getMetaData(headId);
	}
	
	public ZcurdHead getHead(int headId) {
		return ZcurdHead.me.findById(headId);
	}
	
	/**
	 * jdbc方式获取结构信息（关系数据库通用）。有个问题，获取不到表的注释
	 */
	public void genFormByMetaData(final String tableName) {
		Db.execute(new ICallback() {
			@Override
			public Object call(Connection conn) throws SQLException {
				DatabaseMetaData metaData = conn.getMetaData();
				System.out.println(conn.getCatalog());
				ResultSet tableRet = metaData.getTables(null, "%", tableName, new String[]{"TABLE"}); 
				while (tableRet.next())
					System.out.println(tableRet.getString("REMARKS"));

				String columnName;
				String columnType;
				ResultSet colRet = metaData.getColumns(null, "%", tableName, "%");
				while (colRet.next()) {
					columnName = colRet.getString("COLUMN_NAME");
					columnType = colRet.getString("TYPE_NAME");
					int datasize = colRet.getInt("COLUMN_SIZE");
					int digits = colRet.getInt("DECIMAL_DIGITS");
					int nullable = colRet.getInt("NULLABLE");
					System.out.println(columnName + " " + columnType + " "
							+ datasize + " " + digits + " " + nullable + " " + colRet.getString("REMARKS"));
				}
				
				ResultSet pkRSet = metaData.getPrimaryKeys(null, null, tableName);
				while (pkRSet.next()) {
					System.err.println("****** Comment ******");
					System.err.println("TABLE_CAT : " + pkRSet.getObject(1));
					System.err.println("TABLE_SCHEM: " + pkRSet.getObject(2));
					System.err.println("TABLE_NAME : " + pkRSet.getObject(3));
					System.err.println("COLUMN_NAME: " + pkRSet.getObject(4));
					System.err.println("KEY_SEQ : " + pkRSet.getObject(5));
					System.err.println("PK_NAME : " + pkRSet.getObject(6));
					System.err.println("****** ******* ******");
				}
				
				ZcurdHead head = new ZcurdHead();
				head.set("table_name", tableName);
				
				return null;
			}
		});
	}
	
	public void genForm(String tableName, String dbSource) {
		dbSource = ZcurdTool.getDbSource(dbSource);
		String dbName = (String) DBTool.use(dbSource).execute(new ICallback() {
			@Override
			public Object call(Connection conn) throws SQLException {
				return conn.getCatalog();
			}
		});
		String sqlHead = "select * from information_schema.TABLES a where a.TABLE_SCHEMA=? and a.table_name=?";
		Record dbHead = DBTool.use(dbSource).findFirst(sqlHead, new String[]{dbName, tableName});
		ZcurdHead head = new ZcurdHead().set("table_name", dbHead.getStr("TABLE_NAME"))
				.set("form_name", dbHead.getStr("TABLE_COMMENT"))
				.set("db_source", dbSource);
		if(StringUtil.isEmpty(head.getStr("form_name"))) {
			head.set("form_name", head.getStr("table_name"));
		}
		head.save();
		
		String sql = "select * from information_schema.columns a where a.TABLE_SCHEMA=? and a.table_name=?";
		List<Record> fieldList = DBTool.use(dbSource).find(sql, new String[]{dbName, tableName});
		for (Record record : fieldList) {
			String column_name = record.getStr("COLUMN_COMMENT");
			if(StringUtil.isEmpty(column_name)) {
				column_name = record.getStr("COLUMN_NAME");
			}
			//主键
			if("PRI".equals(record.getStr("COLUMN_KEY"))) {
				head.set("id_field", record.getStr("COLUMN_NAME")).update();
			}
			ZcurdField field = new ZcurdField()
				.set("head_id", head.getLong("id").intValue())
				.set("field_name", record.getStr("COLUMN_NAME"))
				.set("column_name", column_name)
				.set("data_type", record.getStr("DATA_TYPE"));
			//不允许为空
			if("NO".equals(record.getStr("IS_NULLABLE"))) {
				field.set("is_allow_null", 0);
			}
			//控件类型
			String dataType = field.getStr("data_type");
			String inputType = "easyui-textbox";
			if(dataType.equals("timestamp") || dataType.equals("date") || dataType.equals("datetime")) {
				inputType = "easyui-datebox";
			}else if(dataType.equals("text")) {
				inputType = "easyui-textbox";	//TODO 扩展支持area和富文本编辑
			}else if(dataType.endsWith("int") || dataType.equals("long")) {
				inputType = "easyui-numberspinner";
			}
			field.set("input_type", inputType);
			field.save();
		}
	}

}
