package com.zhixue.engine.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DeepSeek（OpenAI 兼容协议）客户端：支持流式输出、推理过程、Function Calling 与多模态图片输入。
 */
@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final Json json;
    private final DeepSeekProperties props;
    private final HttpClient http;

    public DeepSeekClient(Json json, DeepSeekProperties props) {
        this.json = json;
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public boolean configured() {
        return props.configured();
    }

    /**
     * 流式对话。会通过 listener 实时推送推理与正文增量，最终返回聚合结果（含工具调用）。
     */
    public LlmResult streamChat(String model,
                                List<Map<String, Object>> messages,
                                List<Map<String, Object>> tools,
                                LlmListener listener) {
        requireKey();
        ObjectNode body = buildBody(model, messages, tools, true, false);
        HttpResponse<Stream<String>> response = send(body);
        Accumulator acc = new Accumulator();
        try (Stream<String> lines = response.body()) {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                if (listener.cancelled()) {
                    acc.cancelled = true;
                    break;
                }
                acc.consume(iterator.next(), listener);
            }
        }
        return acc.toResult();
    }

    /**
     * 非流式一次性补全，用于结构化输出（出题、学习计划等）。
     */
    public LlmResult complete(String model, List<Map<String, Object>> messages, boolean jsonMode) {
        requireKey();
        ObjectNode body = buildBody(model, messages, null, false, jsonMode);
        HttpResponse<Stream<String>> response;
        try {
            response = send(body);
        } catch (LlmException e) {
            if (jsonMode) {
                // 个别部署不支持 response_format，降级重试一次
                body.remove("response_format");
                response = send(body);
            } else {
                throw e;
            }
        }
        String raw = response.body().collect(Collectors.joining("\n"));
        JsonNode node = json.read(raw);
        JsonNode choice = node.path("choices").path(0);
        String content = choice.path("message").path("content").asText("");
        String reasoning = choice.path("message").path("reasoning_content").asText("");
        JsonNode usage = node.path("usage");
        return new LlmResult(content, reasoning, List.of(),
                usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(),
                choice.path("finish_reason").asText(""), false);
    }

    private void requireKey() {
        if (!props.configured()) {
            throw new LlmException("未配置 DEEPSEEK_API_KEY，请在 .env 或环境变量中设置后重启服务");
        }
    }

    private ObjectNode buildBody(String model, List<Map<String, Object>> messages,
                                 List<Map<String, Object>> tools, boolean stream, boolean jsonMode) {
        ObjectNode body = json.obj();
        body.put("model", model == null || model.isBlank() ? props.model() : model);
        body.set("messages", json.mapper().valueToTree(messages));
        body.put("stream", stream);
        body.put("temperature", props.temperature());
        body.put("max_tokens", props.maxTokens());
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", json.mapper().valueToTree(tools));
            body.put("tool_choice", "auto");
        }
        if (jsonMode) {
            body.putObject("response_format").put("type", "json_object");
        }
        if (stream) {
            body.putObject("stream_options").put("include_usage", true);
        }
        return body;
    }

    private HttpResponse<Stream<String>> send(ObjectNode body) {
        String url = props.baseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.write(body), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                String error = response.body().limit(20).collect(Collectors.joining("\n"));
                log.warn("DeepSeek HTTP {}: {}", response.statusCode(), trim(error));
                throw new LlmException("模型服务返回 " + response.statusCode() + "：" + extractError(error));
            }
            return response;
        } catch (IOException e) {
            throw new LlmException("无法连接模型服务：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("模型调用被中断", e);
        }
    }

    private String extractError(String raw) {
        try {
            JsonNode node = json.mapper().readTree(raw);
            String message = node.path("error").path("message").asText("");
            return message.isBlank() ? trim(raw) : message;
        } catch (Exception e) {
            return trim(raw);
        }
    }

    private String trim(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    /** 逐块累积 SSE 增量。 */
    private static final class Accumulator {

        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, ToolCallBuilder> tools = new TreeMap<>();
        private int promptTokens;
        private int completionTokens;
        private String finishReason = "";
        private boolean cancelled;

        void consume(String line, LlmListener listener) {
            if (!line.startsWith("data:")) {
                return;
            }
            String payload = line.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                return;
            }
            JsonNode node;
            try {
                node = JsonHolder.MAPPER.readTree(payload);
            } catch (Exception e) {
                return;
            }
            JsonNode usage = node.path("usage");
            if (usage.isObject()) {
                promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                completionTokens = usage.path("completion_tokens").asInt(completionTokens);
            }
            JsonNode choices = node.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");

            JsonNode reasoningNode = delta.get("reasoning_content");
            if (reasoningNode != null && reasoningNode.isTextual() && !reasoningNode.asText().isEmpty()) {
                reasoning.append(reasoningNode.asText());
                listener.onReasoning(reasoningNode.asText());
            }
            JsonNode contentNode = delta.get("content");
            if (contentNode != null && contentNode.isTextual() && !contentNode.asText().isEmpty()) {
                content.append(contentNode.asText());
                listener.onContent(contentNode.asText());
            }
            JsonNode toolCalls = delta.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode call : toolCalls) {
                    int index = call.path("index").asInt(0);
                    ToolCallBuilder builder = tools.computeIfAbsent(index, key -> new ToolCallBuilder());
                    JsonNode idNode = call.get("id");
                    if (idNode != null && idNode.isTextual()) {
                        builder.id = idNode.asText();
                    }
                    JsonNode function = call.path("function");
                    JsonNode nameNode = function.get("name");
                    if (nameNode != null && nameNode.isTextual()) {
                        builder.name = nameNode.asText();
                    }
                    JsonNode argsNode = function.get("arguments");
                    if (argsNode != null && argsNode.isTextual()) {
                        builder.arguments.append(argsNode.asText());
                    }
                    listener.onToolCallDelta(index, builder.id, builder.name,
                            argsNode != null && argsNode.isTextual() ? argsNode.asText() : "");
                }
            }
            String finish = choice.path("finish_reason").asText("");
            if (!finish.isEmpty()) {
                finishReason = finish;
            }
        }

        LlmResult toResult() {
            List<LlmResult.ToolCall> calls = new ArrayList<>();
            tools.forEach((index, builder) -> {
                if (builder.name != null && !builder.name.isBlank()) {
                    calls.add(new LlmResult.ToolCall(builder.id, builder.name, builder.arguments.toString()));
                }
            });
            return new LlmResult(content.toString(), reasoning.toString(), calls,
                    promptTokens, completionTokens, finishReason, cancelled);
        }
    }

    private static final class ToolCallBuilder {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }

    /** 解析 SSE 行用的共享 mapper，避免每个 chunk 都注入一次。 */
    private static final class JsonHolder {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /** 便捷构造：普通文本消息 */
    public static Map<String, Object> message(String role, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }

    /** 便捷构造：图文混合消息（多模态） */
    public static Map<String, Object> imageMessage(String text, List<String> imageDataUrls) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", text));
        for (String url : imageDataUrls) {
            parts.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", "user");
        map.put("content", parts);
        return map;
    }
}
