package com.zhixue.engine.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.config.DeepSeekProperties;
import com.zhixue.engine.llm.DeepSeekClient;
import com.zhixue.engine.llm.LlmListener;
import com.zhixue.engine.llm.LlmResult;
import com.zhixue.engine.rag.RetrievalHit;
import com.zhixue.engine.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 编排器：负责「规划 -> 工具调用 -> 观察 -> 再生成」的多步循环。
 * <p>
 * 单次请求最多执行 {@code ai.deepseek.max-agent-steps} 步，避免工具循环失控；
 * 若模型在最后一步仍在请求工具，会追加一次不带工具的收尾生成，保证用户总能拿到文本答复。
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final DeepSeekClient client;
    private final DeepSeekProperties props;
    private final ToolRegistry registry;
    private final PromptLibrary prompts;
    private final Json json;

    public AgentOrchestrator(DeepSeekClient client, DeepSeekProperties props, ToolRegistry registry,
                             PromptLibrary prompts, Json json) {
        this.client = client;
        this.props = props;
        this.registry = registry;
        this.prompts = prompts;
        this.json = json;
    }

    public AgentResult run(User user,
                           List<Map<String, Object>> history,
                           String userText,
                           List<String> images,
                           List<RetrievalHit> knowledge,
                           boolean injectionFlagged,
                           Long conversationId,
                           AgentEventSink sink) {
        boolean hasImages = images != null && !images.isEmpty();
        boolean hasKnowledge = knowledge != null && !knowledge.isEmpty();
        String model = hasImages ? props.visionModel() : props.model();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(DeepSeekClient.message("system",
                prompts.buildSystemPrompt(user, hasKnowledge, injectionFlagged)));
        messages.addAll(history);

        String promptText = userText;
        if (hasKnowledge) {
            promptText = userText + "\n\n[可引用资料]\n" + prompts.buildKnowledgeBlock(knowledge)
                    + "\n" + prompts.buildRagInstruction();
        }
        messages.add(hasImages
                ? DeepSeekClient.imageMessage(promptText, images)
                : DeepSeekClient.message("user", promptText));

        if (hasKnowledge) {
            List<Map<String, Object>> citations = new ArrayList<>();
            for (int i = 0; i < knowledge.size(); i++) {
                RetrievalHit hit = knowledge.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", i + 1);
                item.put("documentTitle", hit.documentTitle());
                item.put("seq", hit.seq() + 1);
                item.put("snippet", hit.snippet());
                item.put("score", hit.score());
                citations.add(item);
            }
            sink.onCitations(citations);
        }

        StringBuilder totalContent = new StringBuilder();
        StringBuilder totalReasoning = new StringBuilder();
        List<Map<String, Object>> trace = new ArrayList<>();
        List<Map<String, Object>> discoveredCitations = new ArrayList<>(hasKnowledge ? knowledge.size() : 0);
        int promptTokens = 0;
        int completionTokens = 0;
        boolean cancelled = false;
        int maxSteps = Math.max(1, props.maxAgentSteps());

        for (int step = 1; step <= maxSteps; step++) {
            sink.onStep(step, maxSteps);
            StringBuilder stepContent = new StringBuilder();
            LlmListener listener = new LlmListener() {
                @Override
                public void onReasoning(String delta) {
                    totalReasoning.append(delta);
                    sink.onReasoning(delta);
                }

                @Override
                public void onContent(String delta) {
                    stepContent.append(delta);
                    totalContent.append(delta);
                    sink.onContent(delta);
                }

                @Override
                public boolean cancelled() {
                    return sink.cancelled();
                }
            };

            LlmResult result;
            try {
                result = client.streamChat(model, messages, registry.definitions(), listener);
            } catch (RuntimeException e) {
                log.warn("Agent 第 {} 步失败: {}", step, e.getMessage());
                throw e;
            }
            promptTokens += result.promptTokens();
            completionTokens += result.completionTokens();

            if (result.cancelled() || sink.cancelled()) {
                cancelled = true;
                break;
            }
            if (!result.hasToolCalls()) {
                break;
            }

            messages.add(assistantToolCallMessage(result, stepContent.toString()));
            for (int i = 0; i < result.toolCalls().size(); i++) {
                LlmResult.ToolCall call = result.toolCalls().get(i);
                String callId = call.id() == null || call.id().isBlank()
                        ? "call_" + step + "_" + i : call.id();
                sink.onToolCall(callId, call.name(), call.arguments());

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", callId);
                item.put("name", call.name());
                item.put("arguments", call.arguments());

                ToolResult toolResult;
                try {
                    toolResult = registry.execute(call.name(), parseArguments(call.arguments()),
                            new ToolContext(user.getId(), conversationId));
                    item.put("status", "success");
                } catch (Exception e) {
                    log.warn("工具 {} 执行失败: {}", call.name(), e.getMessage());
                    toolResult = ToolResult.text("工具执行失败：" + e.getMessage());
                    item.put("status", "error");
                }
                item.put("summary", toolResult.summary());
                item.put("artifact", toolResult.artifact());
                trace.add(item);

                sink.onToolResult(callId, call.name(), toolResult.summary(), toolResult.artifact());
                if (!toolResult.citations().isEmpty()) {
                    discoveredCitations.addAll(toolResult.citations());
                }
                messages.add(toolMessage(callId, toolResult.content()));
            }
        }

        if (!cancelled && totalContent.toString().isBlank()) {
            // 兜底收尾：不再提供工具，强制模型输出文本
            LlmListener listener = new LlmListener() {
                @Override
                public void onContent(String delta) {
                    totalContent.append(delta);
                    sink.onContent(delta);
                }

                @Override
                public void onReasoning(String delta) {
                    totalReasoning.append(delta);
                    sink.onReasoning(delta);
                }

                @Override
                public boolean cancelled() {
                    return sink.cancelled();
                }
            };
            try {
                LlmResult result = client.streamChat(model, messages, null, listener);
                promptTokens += result.promptTokens();
                completionTokens += result.completionTokens();
                cancelled = result.cancelled();
            } catch (RuntimeException e) {
                log.warn("兜底生成失败: {}", e.getMessage());
            }
        }

        return new AgentResult(totalContent.toString(), totalReasoning.toString(), trace,
                discoveredCitations, promptTokens, completionTokens, model, cancelled);
    }

    private Map<String, Object> assistantToolCallMessage(LlmResult result, String stepContent) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", stepContent);
        List<Map<String, Object>> calls = new ArrayList<>();
        for (int i = 0; i < result.toolCalls().size(); i++) {
            LlmResult.ToolCall call = result.toolCalls().get(i);
            calls.add(Map.of(
                    "id", call.id() == null || call.id().isBlank() ? "call_" + i : call.id(),
                    "type", "function",
                    "function", Map.of("name", call.name(), "arguments",
                            call.arguments() == null || call.arguments().isBlank() ? "{}" : call.arguments())));
        }
        message.put("tool_calls", calls);
        return message;
    }

    private Map<String, Object> toolMessage(String callId, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", callId);
        message.put("content", content);
        return message;
    }

    private JsonNode parseArguments(String raw) {
        if (raw == null || raw.isBlank()) {
            return json.obj();
        }
        try {
            JsonNode node = json.mapper().readTree(raw);
            return node.isObject() ? node : json.obj();
        } catch (Exception e) {
            return json.obj();
        }
    }
}
