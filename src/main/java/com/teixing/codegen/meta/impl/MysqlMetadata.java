package com.teixing.codegen.meta.impl;

import com.teixing.codegen.meta.AbstractDbMetadata;

import java.sql.SQLException;

/**
 * MySQL 元数据：schema 对应 catalog（database）
 */
public class MysqlMetadata extends AbstractDbMetadata {

    public MysqlMetadata(String url, String username, String password) {
        super(url, username, password);
    }

    @Override
    protected String driverClass() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    protected boolean schemaAsCatalog() {
        return true;
    }

    @Override
    protected String catalog(String schema) throws SQLException {
        return (schema == null || schema.isEmpty()) ? con.getCatalog() : schema;
    }

    @Override
    protected String schemaPattern(String schema) {
        return null;
    }
}
