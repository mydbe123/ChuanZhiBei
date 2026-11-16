package com.zhixue.engine.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 一个可被大模型调用的工具（Function Calling / MCP 思想的本地实现）。
 */
public interface AgentTool {

    String name();

    String description();

    /** JSON Schema 形式的参数定义 */
    Map<String, Object> parameters();

    ToolResult execute(JsonNode arguments, ToolContext context);

    default Map<String, Object> definition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name(),
                        "description", description(),
                        "parameters", parameters()
                ));
    }
}
