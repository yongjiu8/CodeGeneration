package com.teixing.codegen.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 表 -> 实体类 映射模型
 */
public class TableMapping {

    private String tableName;
    private String schema;
    private String className;
    private String packageName;
    private String comment = "";
    private List<ColumnMapping> columns = new ArrayList<>();

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    /** SQL 中使用的带 schema 前缀的表名 */
    public String getFullTableName() {
        return (schema == null || schema.isEmpty()) ? tableName : schema + "." + tableName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getComment() {
        return comment == null ? "" : comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<ColumnMapping> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnMapping> columns) {
        this.columns = columns;
    }

    public List<ColumnMapping> getPrimaryKeys() {
        List<ColumnMapping> list = new ArrayList<>();
        for (ColumnMapping c : columns) {
            if (c.isPrimaryKey()) {
                list.add(c);
            }
        }
        return list;
    }

    public List<ColumnMapping> getNonPrimaryColumns() {
        List<ColumnMapping> list = new ArrayList<>();
        for (ColumnMapping c : columns) {
            if (!c.isPrimaryKey()) {
                list.add(c);
            }
        }
        return list;
    }

    /** 实体类需要的 import 列表 */
    public Set<String> getImports() {
        Set<String> imports = new TreeSet<>();
        for (ColumnMapping c : columns) {
            if ("Date".equals(c.getJavaType())) {
                imports.add("java.util.Date");
            } else if ("BigDecimal".equals(c.getJavaType())) {
                imports.add("java.math.BigDecimal");
            }
        }
        return imports;
    }
}
