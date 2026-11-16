package com.zhixue.engine.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 结构化输出生成器：要求模型返回严格 JSON，并做双层模型降级（推理模型 -> 多模态主力模型）。
 */
@Component
public class StructuredGenerator {

    private static final Logger log = LoggerFactory.getLogger(StructuredGenerator.class);

    private final DeepSeekClient client;
    private final DeepSeekProperties props;
    private final Json json;

    public StructuredGenerator(DeepSeekClient client, DeepSeekProperties props, Json json) {
        this.client = client;
        this.props = props;
        this.json = json;
    }

    public record Result(JsonNode data, String model, int promptTokens, int completionTokens, long latencyMs) {
    }

    public Result generate(String scene, String systemPrompt, String userPrompt) {
        List<Map<String, Object>> messages = List.of(
                DeepSeekClient.message("system", systemPrompt),
                DeepSeekClient.message("user", userPrompt));
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(props.reasoningModel());
        candidates.add(props.model());
        RuntimeException lastError = null;
        for (String model : new ArrayList<>(candidates)) {
            if (model == null || model.isBlank()) {
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                LlmResult result = client.complete(model, messages, true);
                JsonNode node = json.extractJson(result.content());
                return new Result(node, model, result.promptTokens(), result.completionTokens(),
                        System.currentTimeMillis() - start);
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("[{}] 模型 {} 结构化生成失败，尝试降级: {}", scene, model, e.getMessage());
            }
        }
        throw new LlmException("AI 生成失败，请稍后再试",
                lastError);
    }
}
