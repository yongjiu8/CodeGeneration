package com.teixing.codegen.meta;

import com.teixing.codegen.model.TableInfo;
import com.teixing.codegen.model.TableMapping;

import java.sql.SQLException;
import java.util.List;

/**
 * 数据库元数据接口
 */
public interface DbMetadata {

    /** 建立连接 */
    void connect() throws Exception;

    /** schema（MySQL/TiDB 为 database/catalog）列表 */
    List<String> getSchemas() throws SQLException;

    /** 指定 schema 下的表列表（含注释） */
    List<TableInfo> getTables(String schema) throws SQLException;

    /** 读取表完整结构，生成映射模型 */
    TableMapping getTableMapping(String schema, String tableName,
                                 String packageName, String className, String classComment) throws SQLException;

    void close();
}
