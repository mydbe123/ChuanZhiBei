package com.zhixue.engine.llm;

import java.util.List;

/**
 * 一次 LLM 调用的聚合结果。
 */
public record LlmResult(
        String content,
        String reasoning,
        List<ToolCall> toolCalls,
        int promptTokens,
        int completionTokens,
        String finishReason,
        boolean cancelled
) {

    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public record ToolCall(String id, String name, String arguments) {
    }
}
