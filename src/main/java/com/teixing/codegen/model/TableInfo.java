package com.teixing.codegen.model;

/**
 * 表基本信息（表列表展示用）
 */
public class TableInfo {

    private final String name;
    private final String comment;

    public TableInfo(String name, String comment) {
        this.name = name;
        this.comment = comment == null ? "" : comment.trim();
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }
}
