package com.zhixue.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Rag rag, RateLimit rateLimit) {

    public record Jwt(String secret, long ttlHours) {
    }

    public record Rag(int chunkSize, int chunkOverlap, int topK) {
    }

    public record RateLimit(int aiRequestsPerMinute) {
    }
}
