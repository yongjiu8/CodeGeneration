package com.teixing.codegen.meta.impl;

import com.teixing.codegen.meta.AbstractDbMetadata;

/**
 * DB2 元数据：默认 schema 为用户名大写
 */
public class DB2Metadata extends AbstractDbMetadata {

    public DB2Metadata(String url, String username, String password) {
        super(url, username, password);
    }

    @Override
    protected String driverClass() {
        return "com.ibm.db2.jcc.DB2Driver";
    }

    @Override
    protected String schemaPattern(String schema) {
        if (schema == null || schema.isEmpty()) {
            return username == null ? null : username.toUpperCase();
        }
        return schema.toUpperCase();
    }
}
