package com.teixing.codegen.meta;

import com.teixing.codegen.model.ColumnMapping;
import com.teixing.codegen.model.TableInfo;
import com.teixing.codegen.model.TableMapping;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 元数据抽象基类：基于 JDBC DatabaseMetaData 标准 API 读取表结构，
 * 各数据库子类仅需处理 driver / catalog / schema 差异。
 */
public abstract class AbstractDbMetadata implements DbMetadata {

    protected final String url;
    protected final String username;
    protected final String password;
    protected Connection con;

    protected AbstractDbMetadata(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /** JDBC 驱动类名 */
    protected abstract String driverClass();

    /** getTables/getColumns/getPrimaryKeys 的 catalog 参数 */
    protected String catalog(String schema) throws SQLException {
        return null;
    }

    /** getTables/getColumns/getPrimaryKeys 的 schemaPattern 参数 */
    protected String schemaPattern(String schema) throws SQLException {
        return schema;
    }

    /** schema 列表是否取自 catalogs（MySQL/TiDB 的 database 概念） */
    protected boolean schemaAsCatalog() {
        return false;
    }

    @Override
    public void connect() throws Exception {
        Class.forName(driverClass());
        Properties props = new Properties();
        if (username != null) {
            props.put("user", username);
        }
        if (password != null) {
            props.put("password", password);
        }
        props.put("remarksReporting", "true");     // Oracle 取注释必须
        props.put("useInformationSchema", "true"); // MySQL/TiDB 取注释必须
        con = DriverManager.getConnection(url, props);
    }

    @Override
    public List<String> getSchemas() throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        List<String> list = new ArrayList<>();
        try (ResultSet rs = schemaAsCatalog() ? meta.getCatalogs() : meta.getSchemas()) {
            String col = schemaAsCatalog() ? "TABLE_CAT" : "TABLE_SCHEM";
            while (rs.next()) {
                String s = rs.getString(col);
                if (s != null && !s.isEmpty()) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    @Override
    public List<TableInfo> getTables(String schema) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        List<TableInfo> list = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog(schema), schemaPattern(schema), "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name == null || name.contains("$")) {
                    continue;
                }
                list.add(new TableInfo(name, rs.getString("REMARKS")));
            }
        }
        return list;
    }

    @Override
    public TableMapping getTableMapping(String schema, String tableName,
                                        String packageName, String className, String classComment) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();

        Set<String> pkSet = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog(schema), schemaPattern(schema), tableName)) {
            while (rs.next()) {
                pkSet.add(rs.getString("COLUMN_NAME"));
            }
        }

        List<ColumnMapping> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog(schema), schemaPattern(schema), tableName, "%")) {
            while (rs.next()) {
                ColumnMapping c = new ColumnMapping();
                String colName = rs.getString("COLUMN_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                int digits;
                try {
                    digits = rs.getInt("DECIMAL_DIGITS");
                } catch (SQLException e) {
                    digits = 0;
                }
                c.setColumnName(colName);
                c.setPropertyName(toPropertyName(colName));
                c.setDbTypeName(typeName);
                c.setSize(size);
                c.setScale(digits);
                c.setNullable(rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                c.setPrimaryKey(pkSet.contains(colName));
                String remarks = rs.getString("REMARKS");
                c.setComment(remarks == null ? "" : remarks.trim());
                c.setDefaultValue(rs.getString("COLUMN_DEF"));
                c.setJavaType(mapJavaType(dataType, typeName, size, digits));
                c.setJdbcType(mapJdbcType(dataType));
                columns.add(c);
            }
        }

        TableMapping tm = new TableMapping();
        tm.setTableName(tableName);
        tm.setSchema(schema);
        tm.setPackageName(packageName);
        tm.setClassName(className == null || className.isEmpty() ? toClassName(tableName) : className);
        tm.setComment(classComment == null ? "" : classComment);
        tm.setColumns(columns);
        return tm;
    }

    @Override
    public void close() {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ignored) {
            }
            con = null;
        }
    }

    /** 表名 -> 类名：整体小写后按下划线转大驼峰，如 T_USER_INFO -> TUserInfo */
    public static String toClassName(String table) {
        return camel(table, true);
    }

    /** 列名 -> 属性名：整体小写后按下划线转小驼峰，如 USER_NAME -> userName */
    public static String toPropertyName(String column) {
        return camel(column, false);
    }

    private static String camel(String name, boolean firstUpper) {
        String s = name.toLowerCase();
        StringBuilder sb = new StringBuilder(s.length());
        boolean upperNext = firstUpper;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return sb.toString();
    }

    /** java.sql.Types -> Java 类型短名 */
    public static String mapJavaType(int dataType, String typeName, int precision, int scale) {
        switch (dataType) {
            case Types.CHAR:
            case Types.NCHAR:
            case Types.VARCHAR:
            case Types.NVARCHAR:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
                return "String";
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
                return "Integer";
            case Types.BIGINT:
                return "Long";
            case Types.REAL:
                return "Float";
            case Types.FLOAT:
            case Types.DOUBLE:
                return "Double";
            case Types.NUMERIC:
            case Types.DECIMAL:
                return mapNumberType(precision, scale);
            case Types.BIT:
            case Types.BOOLEAN:
                return "Boolean";
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
            case Types.TIME_WITH_TIMEZONE:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "Date";
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB:
                return "byte[]";
            default:
                return "String";
        }
    }

    /** NUMBER/DECIMAL/NUMERIC 统一映射为 java.math.BigDecimal，不再按精度/标度细分 */
    private static String mapNumberType(int precision, int scale) {
        return "BigDecimal";
    }

    /** java.sql.Types -> XML 中的 jdbcType 名 */
    public static String mapJdbcType(int dataType) {
        switch (dataType) {
            case Types.CHAR:
                return "CHAR";
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.VARCHAR:
                return "VARCHAR";
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
                return "LONGVARCHAR";
            case Types.CLOB:
            case Types.NCLOB:
                return "CLOB";
            case Types.TINYINT:
                return "TINYINT";
            case Types.SMALLINT:
                return "SMALLINT";
            case Types.INTEGER:
                return "INTEGER";
            case Types.BIGINT:
                return "BIGINT";
            case Types.REAL:
                return "REAL";
            case Types.FLOAT:
                return "FLOAT";
            case Types.DOUBLE:
                return "DOUBLE";
            case Types.NUMERIC:
                return "NUMERIC";
            case Types.DECIMAL:
                return "DECIMAL";
            case Types.BIT:
            case Types.BOOLEAN:
                return "BIT";
            case Types.DATE:
                return "DATE";
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
                return "TIME";
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "TIMESTAMP";
            case Types.BINARY:
            case Types.VARBINARY:
                return "VARBINARY";
            case Types.LONGVARBINARY:
            case Types.BLOB:
                return "BLOB";
            default:
                return "VARCHAR";
        }
    }
}
