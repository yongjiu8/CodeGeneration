package com.teixing.codegen.meta.impl;

import com.teixing.codegen.meta.AbstractDbMetadata;

import java.sql.SQLException;

/**
 * SQLServer 元数据：默认 schema 为 dbo
 */
public class SqlServerMetadata extends AbstractDbMetadata {

    public SqlServerMetadata(String url, String username, String password) {
        super(url, username, password);
    }

    @Override
    protected String driverClass() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    @Override
    protected String catalog(String schema) throws SQLException {
        return con.getCatalog();
    }

    @Override
    protected String schemaPattern(String schema) {
        return (schema == null || schema.isEmpty()) ? "dbo" : schema;
    }
}
