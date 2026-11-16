package com.zhixue.engine.config;

import com.zhixue.engine.common.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class HealthController {

    private final DeepSeekProperties deepSeek;

    public HealthController(DeepSeekProperties deepSeek) {
        this.deepSeek = deepSeek;
    }

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("app", "智学引擎 ZhiXue Engine");
        payload.put("time", LocalDateTime.now().toString());
        payload.put("aiConfigured", deepSeek.configured());
        payload.put("model", deepSeek.model());
        return ApiResult.ok(payload);
    }
}
