package com.teixing.codegen.meta;

import com.teixing.codegen.meta.impl.DB2Metadata;
import com.teixing.codegen.meta.impl.MysqlMetadata;
import com.teixing.codegen.meta.impl.OracleMetadata;
import com.teixing.codegen.meta.impl.PostgreSQLMetadata;
import com.teixing.codegen.meta.impl.SqlServerMetadata;
import com.teixing.codegen.meta.impl.TidbMetadata;

/**
 * 按数据库类型创建元数据实现
 */
public final class DbMetadataFactory {

    public static final class DbType {
        public final String label;
        public final String urlTemplate;

        public DbType(String label, String urlTemplate) {
            this.label = label;
            this.urlTemplate = urlTemplate;
        }
    }

    public static final DbType[] DB_TYPES = {
            new DbType("TiDB", "jdbc:mysql://127.0.0.1:4000/iplat4j?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"),
            new DbType("MySQL", "jdbc:mysql://127.0.0.1:3306/iplat4j?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"),
            new DbType("Oracle", "jdbc:oracle:thin:@//127.0.0.1:1521/orcl"),
            new DbType("DB2", "jdbc:db2://127.0.0.1:50000/sample"),
            new DbType("PostgreSQL", "jdbc:postgresql://127.0.0.1:5432/postgres"),
            new DbType("SQLServer", "jdbc:sqlserver://127.0.0.1:1433;databaseName=master"),
    };

    private DbMetadataFactory() {
    }

    public static String[] getDbTypeLabels() {
        String[] labels = new String[DB_TYPES.length];
        for (int i = 0; i < DB_TYPES.length; i++) {
            labels[i] = DB_TYPES[i].label;
        }
        return labels;
    }

    public static DbMetadata create(String typeLabel, String url, String username, String password) {
        switch (typeLabel) {
            case "TiDB":
                return new TidbMetadata(url, username, password);
            case "MySQL":
                return new MysqlMetadata(url, username, password);
            case "Oracle":
                return new OracleMetadata(url, username, password);
            case "DB2":
                return new DB2Metadata(url, username, password);
            case "PostgreSQL":
                return new PostgreSQLMetadata(url, username, password);
            case "SQLServer":
                return new SqlServerMetadata(url, username, password);
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + typeLabel);
        }
    }
}
