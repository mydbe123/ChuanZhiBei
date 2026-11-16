package com.zhixue.engine.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> discovered) {
        discovered.forEach(tool -> tools.put(tool.name(), tool));
    }

    public List<Map<String, Object>> definitions() {
        return tools.values().stream().map(AgentTool::definition).toList();
    }

    public ToolResult execute(String name, JsonNode arguments, ToolContext context) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new BusinessException(400, "未知工具：" + name);
        }
        return tool.execute(arguments, context);
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }
}
