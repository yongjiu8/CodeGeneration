package com.teixing.codegen.generator;

import com.teixing.codegen.model.ColumnMapping;
import com.teixing.codegen.model.TableMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 代码生成器：
 * - 经典模板：iplat4j DaoEPBase 实体 + iBatis2 sqlmap（与 baosight 项目现有文件格式一致）
 * - MyBatis 模板：简单 POJO 实体 + MyBatis3 mapper xml
 * 文件已存在时直接覆盖。
 */
public final class CodeGenerator {

    private CodeGenerator() {
    }

    // ================================================== 经典模板（iplat4j）

    public static Path generateDomainClassic(TableMapping tm, Path dir) throws IOException {
        Files.createDirectories(dir);
        boolean hasNumber = false;
        for (ColumnMapping c : tm.getColumns()) {
            if (isNumber(c)) {
                hasNumber = true;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("/**\n* Generate time : ").append(now()).append("\n* Version : 1.0\n*/\n");
        sb.append("package ").append(tm.getPackageName()).append(";\n\n");
        sb.append("import com.baosight.iplat4j.core.data.DaoEPBase;\n");
        sb.append("import com.baosight.iplat4j.core.ei.EiColumn;\n");
        if (hasNumber) {
            sb.append("import com.baosight.iplat4j.core.util.NumberUtils;\n");
        }
        sb.append("import com.baosight.iplat4j.core.util.StringUtils;\n\n");
        for (String imp : tm.getImports()) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("import java.util.HashMap;\n");
        sb.append("import java.util.Map;\n\n");

        sb.append("/**\n* ").append(tm.getClassName()).append('\n');
        sb.append("* table comment : ").append(tm.getComment()).append("\n*/\n");
        sb.append("public class ").append(tm.getClassName()).append(" extends DaoEPBase {\n\n");

        // 字段
        for (ColumnMapping c : tm.getColumns()) {
            sb.append("                private ").append(c.getJavaType()).append(' ').append(c.getPropertyName());
            String def = defaultValue(c.getJavaType());
            if (!def.isEmpty()) {
                sb.append(" = ").append(def);
            }
            sb.append(";\t\t/* ").append(c.getComment()).append("*/\n");
        }
        sb.append('\n');

        // initMetaData
        sb.append("    /**\n* initialize the metadata\n*/\n");
        sb.append("public void initMetaData() {\nEiColumn eiColumn;\n\n");
        for (ColumnMapping c : tm.getColumns()) {
            sb.append("        eiColumn = new EiColumn(\"").append(c.getPropertyName()).append("\");\n");
            if (isNumber(c)) {
                sb.append("        eiColumn.setType(\"N\");\n");
                sb.append("        eiColumn.setScaleLength(").append(c.getScale()).append(");\n");
            }
            if (!"byte[]".equals(c.getJavaType()) && c.getSize() > 0) {
                sb.append("        eiColumn.setFieldLength(").append(c.getSize()).append(");\n");
            }
            sb.append("        eiColumn.setDescName(\"").append(c.getComment()).append("\");\n");
            sb.append("        eiMetadata.addMeta(eiColumn);\n\n");
        }
        sb.append("}\n");

        // 构造方法
        sb.append("/**\n* the constructor\n*/\n");
        sb.append("public ").append(tm.getClassName()).append("() {\ninitMetaData();\n}\n\n");

        // getter / setter
        for (ColumnMapping c : tm.getColumns()) {
            String cap = capitalize(c.getPropertyName());
            sb.append("        /**\n");
            sb.append("        * get the ").append(c.getPropertyName()).append(" - ").append(c.getComment()).append('\n');
            sb.append("        * @return the ").append(c.getPropertyName()).append('\n');
            sb.append("        */\n");
            sb.append("        public ").append(c.getJavaType()).append(" get").append(cap).append("() {\n");
            sb.append("        return this.").append(c.getPropertyName()).append(";\n");
            sb.append("        }\n\n");
            sb.append("        /**\n");
            sb.append("        * set the ").append(c.getPropertyName()).append(" - ").append(c.getComment()).append('\n');
            sb.append("        */\n");
            sb.append("        public void set").append(cap).append('(').append(c.getJavaType())
                    .append(' ').append(c.getPropertyName()).append(") {\n");
            sb.append("        this.").append(c.getPropertyName()).append(" = ").append(c.getPropertyName()).append(";\n");
            sb.append("        }\n");
        }
        sb.append('\n');

        // fromMap
        sb.append("    /**\n* get the value from Map\n*/\n");
        sb.append("public void fromMap(Map map) {\n\n");
        for (ColumnMapping c : tm.getColumns()) {
            String cap = capitalize(c.getPropertyName());
            String prop = c.getPropertyName();
            switch (c.getJavaType()) {
                case "String":
                    sb.append("                set").append(cap).append("(StringUtils.defaultIfEmpty(StringUtils.toString(map.get(\"")
                            .append(prop).append("\")), ").append(prop).append("));\n");
                    break;
                case "BigDecimal":
                case "Long":
                case "Integer":
                case "Double":
                case "Float":
                    sb.append("                set").append(cap).append("(NumberUtils.to").append(c.getJavaType())
                            .append("(StringUtils.toString(map.get(\"").append(prop).append("\")), ")
                            .append(prop).append("));\n");
                    break;
                default:
                    sb.append("                //TODO ").append(prop).append(" cannot generate automatically ,")
                            .append(c.getJavaType()).append(" dont support\n");
                    break;
            }
        }
        sb.append("}\n\n");

        // toMap
        sb.append("/**\n* set the value to Map\n*/\n");
        sb.append("public Map toMap() {\n\nMap map = new HashMap();\n");
        for (ColumnMapping c : tm.getColumns()) {
            String prop = c.getPropertyName();
            sb.append("                map.put(\"").append(prop).append("\",StringUtils.toString(")
                    .append(prop).append(", eiMetadata.getMeta(\"").append(prop).append("\")));\n");
        }
        sb.append("    return map;\n\n}\n}\n");

        Path file = dir.resolve(tm.getClassName() + ".java");
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** iBatis2 sqlmap（baosight 经典格式）。fileSuffix 用于 TiDB 输出 .tidb.xml */
    public static Path generateIBatisXml(TableMapping tm, Path dir, String fileBaseName, String fileSuffix) throws IOException {
        Files.createDirectories(dir);
        String fqcn = tm.getPackageName() + "." + tm.getClassName();
        List<ColumnMapping> pks = tm.getPrimaryKeys();
        List<ColumnMapping> cols = tm.getColumns();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE sqlMap PUBLIC \"-//iBATIS.com//DTD SQL Map 2.0//EN\" \"http://www.ibatis.com/dtd/sql-map-2.dtd\">\n");

        // 头部表结构注释
        sb.append("\t<!--      table information\n");
        sb.append("\t\tGenerate time : ").append(now()).append('\n');
        sb.append("   \t\tVersion :  1.0\n");
        sb.append("\t\ttable comment : ").append(tm.getComment()).append('\n');
        sb.append("\t\ttableName :").append(tm.getFullTableName()).append('\n');
        for (int i = 0; i < cols.size(); i++) {
            ColumnMapping c = cols.get(i);
            sb.append("\t\t ").append(c.getColumnName()).append("  ").append(c.getDbTypeName());
            if (!c.isNullable()) {
                sb.append("   NOT NULL");
            }
            if (c.isPrimaryKey()) {
                sb.append("   primarykey");
            }
            if (i < cols.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("\t-->\n");

        sb.append("<sqlMap namespace=\"").append(fileBaseName).append("\">\n\n");

        // query
        sb.append("\t<select id=\"query\" parameterClass=\"java.util.Map\"\n");
        sb.append("\t\t\tresultClass=\"").append(fqcn).append("\">\n");
        sb.append("\t\tSELECT\n");
        for (int i = 0; i < cols.size(); i++) {
            ColumnMapping c = cols.get(i);
            sb.append("\t\t\t\t").append(c.getColumnName()).append("\tas \"").append(c.getPropertyName()).append('"');
            if (i < cols.size() - 1) {
                sb.append(',');
            }
            appendXmlComment(sb, c.getComment());
            sb.append('\n');
        }
        sb.append("\t\tFROM ").append(tm.getFullTableName()).append(" WHERE 1=1\n");
        for (ColumnMapping pk : pks) {
            sb.append("\t\t<isNotEmpty prepend=\" AND \" property=\"").append(pk.getPropertyName()).append("\">\n");
            sb.append("\t\t\t").append(pk.getColumnName()).append(" = #").append(pk.getPropertyName()).append("#\n");
            sb.append("\t\t</isNotEmpty>\n");
        }
        if (!pks.isEmpty()) {
            sb.append("\t\t<dynamic prepend=\"ORDER BY\">\n");
            sb.append("         <isNotEmpty property=\"orderBy\">\n");
            sb.append("    \t\t  $orderBy$\n");
            sb.append("   \t\t </isNotEmpty>\n");
            sb.append("   \t\t<isEmpty property=\"orderBy\">\n");
            sb.append("    \t\t  ").append(pks.get(0).getColumnName()).append(" asc\n");
            sb.append("\t\t</isEmpty>\n");
            sb.append("  \t\t</dynamic>\n");
        }
        sb.append('\n');
        sb.append("\t</select>\n\n");

        // count
        sb.append("\t<select id=\"count\" resultClass=\"int\">\n");
        sb.append("\t\tSELECT COUNT(*) FROM ").append(tm.getFullTableName()).append(" WHERE 1=1\n");
        for (ColumnMapping pk : pks) {
            sb.append("\t\t<isNotEmpty prepend=\" AND \" property=\"").append(pk.getPropertyName()).append("\">\n");
            sb.append("\t\t\t").append(pk.getColumnName()).append(" = #").append(pk.getPropertyName()).append("#\n");
            sb.append("\t\t</isNotEmpty>\n");
        }
        sb.append("\t</select>\n\n");

        // 全列条件参考注释块
        sb.append("\t<!--\n");
        for (ColumnMapping c : cols) {
            sb.append("\t\t<isNotEmpty prepend=\" AND \" property=\"").append(c.getPropertyName()).append("\">\n");
            sb.append("\t\t\t").append(c.getColumnName()).append(" = #").append(c.getPropertyName()).append("#\n");
            sb.append("\t\t</isNotEmpty>\n");
        }
        sb.append("\t-->\n\n");

        // insert
        sb.append("\t<insert id=\"insert\">\n");
        for (int i = 0; i < cols.size(); i++) {
            ColumnMapping c = cols.get(i);
            if (i == 0) {
                sb.append("\t\tINSERT INTO ").append(tm.getFullTableName()).append(" (").append(c.getColumnName()).append(',');
            } else {
                sb.append("\t\t").append(c.getColumnName());
                if (i < cols.size() - 1) {
                    sb.append(',');
                }
            }
            appendXmlComment(sb, c.getComment());
            sb.append('\n');
        }
        sb.append("\t\t)\n");
        sb.append("\t    VALUES (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ibatisParam(cols.get(i), true));
        }
        sb.append(")\n\t</insert>\n\n");

        // delete
        sb.append("\t<delete id=\"delete\">\n");
        sb.append("\t\tDELETE FROM ").append(tm.getFullTableName());
        if (!pks.isEmpty()) {
            sb.append(" WHERE\n");
            sb.append("\t\t").append(pkSharpCondition(pks)).append('\n');
        } else {
            sb.append(" <!-- TODO 该表无主键，请手动维护删除条件 -->\n");
        }
        sb.append("\t</delete>\n\n");

        // update
        sb.append("\t<update id=\"update\">\n");
        sb.append("\t\tUPDATE ").append(tm.getFullTableName()).append('\n');
        sb.append("\t\tSET\n");
        List<ColumnMapping> nonPk = tm.getNonPrimaryColumns();
        for (int i = 0; i < nonPk.size(); i++) {
            ColumnMapping c = nonPk.get(i);
            sb.append("\t\t").append(c.getColumnName()).append("\t= ").append(ibatisParam(c, false));
            if (i < nonPk.size() - 1) {
                sb.append(',');
            }
            appendXmlComment(sb, c.getComment());
            sb.append('\n');
        }
        if (!pks.isEmpty()) {
            sb.append("\t\tWHERE\n");
            sb.append("\t\t").append(pkSharpCondition(pks)).append('\n');
        } else {
            sb.append("\t\t<!-- TODO 该表无主键，请手动维护更新条件 -->\n");
        }
        sb.append("\t</update>\n\n");

        sb.append("</sqlMap>\n");

        Path file = dir.resolve(fileBaseName + fileSuffix + ".xml");
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ================================================== MyBatis 模板

    public static Path generateDomain(TableMapping tm, Path dir) throws IOException {
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(tm.getPackageName()).append(";\n\n");
        sb.append("import java.io.Serializable;\n");
        for (String imp : tm.getImports()) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append('\n');
        sb.append("/**\n");
        sb.append(" * ").append(tm.getComment().isEmpty() ? tm.getClassName() : tm.getComment())
                .append("（对应表：").append(tm.getFullTableName()).append("）\n");
        sb.append(" *\n");
        sb.append(" * @author codeGeneration\n");
        sb.append(" * @date ").append(now()).append('\n');
        sb.append(" */\n");
        sb.append("public class ").append(tm.getClassName()).append(" implements Serializable {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");

        for (ColumnMapping c : tm.getColumns()) {
            if (!c.getComment().isEmpty()) {
                sb.append("    /** ").append(c.getComment()).append(" */\n");
            }
            sb.append("    private ").append(c.getJavaType()).append(' ').append(c.getPropertyName()).append(";\n\n");
        }

        for (ColumnMapping c : tm.getColumns()) {
            String cap = capitalize(c.getPropertyName());
            sb.append("    public ").append(c.getJavaType()).append(" get").append(cap).append("() {\n");
            sb.append("        return ").append(c.getPropertyName()).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(cap).append('(').append(c.getJavaType())
                    .append(' ').append(c.getPropertyName()).append(") {\n");
            sb.append("        this.").append(c.getPropertyName()).append(" = ").append(c.getPropertyName()).append(";\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");

        Path file = dir.resolve(tm.getClassName() + ".java");
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    public static Path generateMybatisXml(TableMapping tm, Path dir, String fileBaseName) throws IOException {
        Files.createDirectories(dir);
        String fqcn = tm.getPackageName() + "." + tm.getClassName();
        List<ColumnMapping> pks = tm.getPrimaryKeys();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n");
        sb.append("<!-- 由 codeGeneration 插件生成于 ").append(now()).append(" -->\n");
        sb.append("<mapper namespace=\"").append(fqcn).append("\">\n\n");

        sb.append("    <resultMap id=\"resultMap\" type=\"").append(fqcn).append("\">\n");
        for (ColumnMapping c : tm.getColumns()) {
            sb.append(c.isPrimaryKey() ? "        <id" : "        <result")
                    .append(" property=\"").append(c.getPropertyName()).append("\"")
                    .append(" column=\"").append(c.getColumnName()).append("\"")
                    .append(" jdbcType=\"").append(c.getJdbcType()).append("\"/>\n");
        }
        sb.append("    </resultMap>\n\n");

        sb.append("    <sql id=\"Base_Column_List\">\n        ");
        sb.append(joinColumnNames(tm.getColumns()));
        sb.append("\n    </sql>\n\n");

        if (!pks.isEmpty()) {
            sb.append("    <select id=\"queryById\" parameterType=\"").append(pkParamType(pks))
                    .append("\" resultMap=\"resultMap\">\n");
            sb.append("        SELECT <include refid=\"Base_Column_List\"/>\n");
            sb.append("        FROM ").append(tm.getFullTableName()).append('\n');
            sb.append("        WHERE ").append(pkCondition(pks)).append('\n');
            sb.append("    </select>\n\n");
        }

        sb.append("    <select id=\"queryList\" parameterType=\"").append(fqcn).append("\" resultMap=\"resultMap\">\n");
        sb.append("        SELECT <include refid=\"Base_Column_List\"/>\n");
        sb.append("        FROM ").append(tm.getFullTableName()).append('\n');
        sb.append("        <where>\n");
        for (ColumnMapping c : tm.getColumns()) {
            sb.append("            <if test=\"").append(ifTest(c)).append("\">AND ")
                    .append(c.getColumnName()).append(" = #{").append(c.getPropertyName())
                    .append(",jdbcType=").append(c.getJdbcType()).append("}</if>\n");
        }
        sb.append("        </where>\n");
        sb.append("    </select>\n\n");

        sb.append("    <select id=\"count\" parameterType=\"").append(fqcn).append("\" resultType=\"long\">\n");
        sb.append("        SELECT COUNT(1) FROM ").append(tm.getFullTableName()).append('\n');
        sb.append("        <where>\n");
        for (ColumnMapping c : tm.getColumns()) {
            sb.append("            <if test=\"").append(ifTest(c)).append("\">AND ")
                    .append(c.getColumnName()).append(" = #{").append(c.getPropertyName())
                    .append(",jdbcType=").append(c.getJdbcType()).append("}</if>\n");
        }
        sb.append("        </where>\n");
        sb.append("    </select>\n\n");

        sb.append("    <insert id=\"insert\" parameterType=\"").append(fqcn).append("\">\n");
        sb.append("        INSERT INTO ").append(tm.getFullTableName()).append('\n');
        sb.append("        (").append(joinColumnNames(tm.getColumns())).append(")\n");
        sb.append("        VALUES (");
        for (int i = 0; i < tm.getColumns().size(); i++) {
            ColumnMapping c = tm.getColumns().get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("#{").append(c.getPropertyName()).append(",jdbcType=").append(c.getJdbcType()).append('}');
        }
        sb.append(")\n    </insert>\n\n");

        if (!pks.isEmpty()) {
            sb.append("    <update id=\"update\" parameterType=\"").append(fqcn).append("\">\n");
            sb.append("        UPDATE ").append(tm.getFullTableName()).append('\n');
            sb.append("        <set>\n");
            for (ColumnMapping c : tm.getNonPrimaryColumns()) {
                sb.append("            <if test=\"").append(c.getPropertyName()).append(" != null\">")
                        .append(c.getColumnName()).append(" = #{").append(c.getPropertyName())
                        .append(",jdbcType=").append(c.getJdbcType()).append("},</if>\n");
            }
            sb.append("        </set>\n");
            sb.append("        WHERE ").append(pkCondition(pks)).append('\n');
            sb.append("    </update>\n\n");

            sb.append("    <delete id=\"deleteById\" parameterType=\"").append(pkParamType(pks)).append("\">\n");
            sb.append("        DELETE FROM ").append(tm.getFullTableName()).append('\n');
            sb.append("        WHERE ").append(pkCondition(pks)).append('\n');
            sb.append("    </delete>\n");
        } else {
            sb.append("    <!-- 该表无主键，update / delete / queryById 请手动维护条件 -->\n");
        }

        sb.append("</mapper>\n");

        Path file = dir.resolve(fileBaseName + ".xml");
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ================================================== helper

    private static boolean isNumber(ColumnMapping c) {
        switch (c.getJavaType()) {
            case "BigDecimal":
            case "Long":
            case "Integer":
            case "Double":
            case "Float":
                return true;
            default:
                return false;
        }
    }

    private static String defaultValue(String javaType) {
        switch (javaType) {
            case "String":
                return "\" \"";
            case "BigDecimal":
                return "new BigDecimal(\"0\")";
            case "Long":
                return "new Long(0)";
            case "Integer":
                return "new Integer(0)";
            case "Double":
                return "0d";
            case "Float":
                return "0f";
            case "Boolean":
                return "false";
            case "Date":
                return "new Date()";
            default:
                return "";
        }
    }

    /** iBatis 行内注释：有注释时输出 "  <!-- xxx -->"（并防止注释中出现 -- 破坏 XML） */
    private static void appendXmlComment(StringBuilder sb, String comment) {
        if (comment != null && !comment.isEmpty()) {
            sb.append("  <!-- ").append(comment.replace("--", "——")).append(" -->");
        }
    }

    /** iBatis 参数：#prop:VARCHAR# / #prop:NUMERIC# / #prop#；insertWithVarchar=false 时 VARCHAR 不带后缀（update/delete 场景） */
    private static String ibatisParam(ColumnMapping c, boolean insertWithVarchar) {
        String type = sqlTypeOf(c);
        StringBuilder sb = new StringBuilder("#").append(c.getPropertyName());
        if ("NUMERIC".equals(type) || (insertWithVarchar && "VARCHAR".equals(type))) {
            sb.append(':').append(type);
        }
        return sb.append('#').toString();
    }

    /** 列方言归类：String -> VARCHAR；数字 -> NUMERIC；其他（BLOB 等） -> 无 */
    private static String sqlTypeOf(ColumnMapping c) {
        if ("String".equals(c.getJavaType())) {
            return "VARCHAR";
        }
        return isNumber(c) ? "NUMERIC" : "";
    }

    /** 主键 #prop# 条件：PK = #pk# AND ... */
    private static String pkSharpCondition(List<ColumnMapping> pks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pks.size(); i++) {
            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append(pks.get(i).getColumnName()).append(" = #").append(pks.get(i).getPropertyName()).append('#');
        }
        return sb.toString();
    }

    private static String joinColumnNames(List<ColumnMapping> columns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getColumnName());
        }
        return sb.toString();
    }

    private static String pkCondition(List<ColumnMapping> pks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pks.size(); i++) {
            ColumnMapping c = pks.get(i);
            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append(c.getColumnName()).append(" = ")
                    .append("#{").append(c.getPropertyName()).append(",jdbcType=").append(c.getJdbcType()).append('}');
        }
        return sb.toString();
    }

    private static String pkParamType(List<ColumnMapping> pks) {
        if (pks.size() == 1) {
            return fqType(pks.get(0).getJavaType());
        }
        return "map";
    }

    private static String fqType(String javaType) {
        switch (javaType) {
            case "Date":
                return "java.util.Date";
            case "BigDecimal":
                return "java.math.BigDecimal";
            case "byte[]":
                return "byte[]";
            default:
                return "java.lang." + javaType;
        }
    }

    /** String 属性判空加 ''，其余只判 null */
    private static String ifTest(ColumnMapping c) {
        if ("String".equals(c.getJavaType())) {
            return c.getPropertyName() + " != null and " + c.getPropertyName() + " != ''";
        }
        return c.getPropertyName() + " != null";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
