package com.zhixue.engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new AppProperties(
                    new AppProperties.Jwt("unit-test-secret-key-at-least-32-characters", 1),
                    new AppProperties.Rag(400, 60, 5),
                    new AppProperties.RateLimit(10)),
            new Json(new ObjectMapper()));

    @Test
    void roundTripsUserId() {
        String token = jwtService.issue(42L, "demo");

        assertThat(jwtService.verify(token)).contains(42L);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issue(42L, "demo");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.verify(tampered)).isEmpty();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(jwtService.verify("not-a-token")).isEmpty();
        assertThat(jwtService.verify(null)).isEmpty();
    }
}
