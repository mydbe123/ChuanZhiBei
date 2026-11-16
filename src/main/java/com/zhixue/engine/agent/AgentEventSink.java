package com.zhixue.engine.agent;

import java.util.List;
import java.util.Map;

/** Agent 运行过程中的事件回调，用于驱动 SSE 推流。 */
public interface AgentEventSink {

    AgentEventSink NOOP = new AgentEventSink() {
    };

    default void onStep(int step, int maxSteps) {
    }

    default void onReasoning(String delta) {
    }

    default void onContent(String delta) {
    }

    default void onToolCall(String id, String name, String arguments) {
    }

    default void onToolResult(String id, String name, String summary, Map<String, Object> artifact) {
    }

    default void onCitations(List<Map<String, Object>> citations) {
    }

    default boolean cancelled() {
        return false;
    }
}
