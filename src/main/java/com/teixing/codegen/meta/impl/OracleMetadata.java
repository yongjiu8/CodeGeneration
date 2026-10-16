package com.teixing.codegen.meta.impl;

import com.teixing.codegen.meta.AbstractDbMetadata;

/**
 * Oracle 元数据：默认 schema 为用户名大写
 */
public class OracleMetadata extends AbstractDbMetadata {

    public OracleMetadata(String url, String username, String password) {
        super(url, username, password);
    }

    @Override
    protected String driverClass() {
        return "oracle.jdbc.OracleDriver";
    }

    @Override
    protected String schemaPattern(String schema) {
        if (schema == null || schema.isEmpty()) {
            return username == null ? null : username.toUpperCase();
        }
        return schema.toUpperCase();
    }
}
