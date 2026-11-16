package com.zhixue.engine.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 轻量 JWT（HS256）实现，避免为一个演示项目引入完整的 Spring Security 过滤器链。
 */
@Service
public class JwtService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER = ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final long ttlSeconds;
    private final Json json;

    public JwtService(AppProperties properties, Json json) {
        this.secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = properties.jwt().ttlHours() * 3600;
        this.json = json;
    }

    public String issue(long userId, String username) {
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = ENCODER.encodeToString(("{\"sub\":\"" + userId + "\",\"usr\":\""
                + username.replace("\"", "") + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        String signingInput = HEADER + "." + payload;
        return signingInput + "." + ENCODER.encodeToString(hmac(signingInput));
    }

    public Optional<Long> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, DECODER.decode(parts[2]))) {
                return Optional.empty();
            }
            JsonNode payload = json.read(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8));
            if (payload.path("exp").asLong() < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            long userId = payload.path("sub").asLong(0);
            return userId > 0 ? Optional.of(userId) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }
}
