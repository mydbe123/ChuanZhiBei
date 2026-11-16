package com.zhixue.engine.agent;

import java.util.List;
import java.util.Map;

/**
 * @param content  回填给大模型的工具结果文本
 * @param summary  展示给用户的简短摘要
 * @param artifact 结构化产物（如新建的计划/测验），前端可渲染为卡片
 * @param citations 若工具产生引用来源，则带出
 */
public record ToolResult(String content, String summary, Map<String, Object> artifact,
                         List<Map<String, Object>> citations) {

    public static ToolResult text(String content) {
        return new ToolResult(content, null, Map.of(), List.of());
    }

    public static ToolResult of(String content, String summary, Map<String, Object> artifact,
                                List<Map<String, Object>> citations) {
        return new ToolResult(content, summary, artifact == null ? Map.of() : artifact,
                citations == null ? List.of() : citations);
    }
}
