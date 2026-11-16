package com.zhixue.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.deepseek")
public record DeepSeekProperties(
        String baseUrl,
        String apiKey,
        String model,
        String visionModel,
        String reasoningModel,
        double temperature,
        int maxTokens,
        int timeoutSeconds,
        int maxAgentSteps
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
