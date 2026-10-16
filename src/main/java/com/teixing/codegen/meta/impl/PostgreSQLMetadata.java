package com.teixing.codegen.meta.impl;

import com.teixing.codegen.meta.AbstractDbMetadata;

/**
 * PostgreSQL 元数据：默认 schema 为 public
 */
public class PostgreSQLMetadata extends AbstractDbMetadata {

    public PostgreSQLMetadata(String url, String username, String password) {
        super(url, username, password);
    }

    @Override
    protected String driverClass() {
        return "org.postgresql.Driver";
    }

    @Override
    protected String schemaPattern(String schema) {
        return (schema == null || schema.isEmpty()) ? "public" : schema;
    }
}
