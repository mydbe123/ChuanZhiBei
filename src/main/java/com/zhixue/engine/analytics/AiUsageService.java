package com.zhixue.engine.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private final AiUsageLogRepository repository;

    public AiUsageService(AiUsageLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(Long userId, String scene, String model, int promptTokens, int completionTokens,
                       long latencyMs, String status, boolean blocked, String detail) {
        try {
            repository.save(AiUsageLog.of(userId, scene, model)
                    .tokens(promptTokens, completionTokens)
                    .latency((int) Math.min(Integer.MAX_VALUE, latencyMs))
                    .status(status, detail)
                    .blocked(blocked));
        } catch (Exception e) {
            log.warn("AI 审计日志写入失败: {}", e.getMessage());
        }
    }
}
