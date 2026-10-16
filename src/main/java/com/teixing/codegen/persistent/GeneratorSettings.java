package com.teixing.codegen.persistent;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 生成器配置持久化（保存到项目 .idea/teixing-codegen.xml）
 */
@State(name = "TeixingCodeGeneration", storages = {@Storage("teixing-codegen.xml")})
public class GeneratorSettings implements PersistentStateComponent<GeneratorSettings.State> {

    public static class State {
        public String dbType = "TiDB";
        public String url = "";
        public String username = "";
        public String password = "";
        public String schema = "";
        public String domainPath = "";
        public String template = "iBatis2经典模板";
        public boolean genDomain = true;
        public boolean genSqlmap = true;
        /** 每种数据库类型上次使用的连接地址 */
        public java.util.Map<String, String> urlCache = new java.util.LinkedHashMap<>();
    }

    private State state = new State();

    public static GeneratorSettings getInstance(Project project) {
        return project.getService(GeneratorSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}
