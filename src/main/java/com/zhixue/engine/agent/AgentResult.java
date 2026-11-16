package com.zhixue.engine.agent;

import java.util.List;
import java.util.Map;

public record AgentResult(
        String content,
        String reasoning,
        List<Map<String, Object>> toolTrace,
        List<Map<String, Object>> citations,
        int promptTokens,
        int completionTokens,
        String model,
        boolean cancelled
) {
}
