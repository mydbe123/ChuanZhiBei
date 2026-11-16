package com.zhixue.engine.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * AI 调用审计：用于可观测性、配额统计与安全回溯。
 */
@Entity
@Table(name = "zx_ai_usage_logs")
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 32)
    private String scene;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(nullable = false, length = 16)
    private String status = "OK";

    @Column(nullable = false)
    private boolean blocked;

    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public static AiUsageLog of(Long userId, String scene, String model) {
        AiUsageLog log = new AiUsageLog();
        log.userId = userId;
        log.scene = scene;
        log.model = model == null ? "unknown" : model;
        return log;
    }

    public AiUsageLog tokens(int prompt, int completion) {
        this.promptTokens = prompt;
        this.completionTokens = completion;
        return this;
    }

    public AiUsageLog latency(int ms) {
        this.latencyMs = ms;
        return this;
    }

    public AiUsageLog status(String status, String detail) {
        this.status = status;
        this.detail = detail != null && detail.length() > 240 ? detail.substring(0, 240) : detail;
        return this;
    }

    public AiUsageLog blocked(boolean blocked) {
        this.blocked = blocked;
        return this;
    }

    /** 供演示数据导入使用，可指定历史时间 */
    public AiUsageLog at(LocalDateTime time) {
        this.createdAt = time;
        return this;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getScene() {
        return scene;
    }

    public String getModel() {
        return model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
