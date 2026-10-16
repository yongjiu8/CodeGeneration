package com.teixing.codegen.meta.impl;

/**
 * TiDB 元数据：TiDB 兼容 MySQL 协议，复用 MySQL 驱动与元数据读取逻辑，
 * 默认端口 4000（见 DbMetadataFactory 中的 URL 模板）。
 */
public class TidbMetadata extends MysqlMetadata {

    public TidbMetadata(String url, String username, String password) {
        super(url, username, password);
    }
}
