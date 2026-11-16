package com.zhixue.engine.llm;

/**
 * 流式回调。推理过程（reasoning_content）与正文分别推送，供前端做“思考过程可视化”。
 */
public interface LlmListener {

    LlmListener NOOP = new LlmListener() {
    };

    default void onReasoning(String delta) {
    }

    default void onContent(String delta) {
    }

    default void onToolCallDelta(int index, String id, String name, String argumentsDelta) {
    }

    /** 客户端主动中止（关闭页面 / 点击停止）时返回 true，服务端会尽快结束流。 */
    default boolean cancelled() {
        return false;
    }
}
