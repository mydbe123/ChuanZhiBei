package com.zhixue.engine.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "zx_learning_events")
public class LearningEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "knowledge_point", length = 64)
    private String knowledgePoint;

    private Double score;

    private int minutes;

    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public static LearningEvent of(long userId, String type) {
        LearningEvent event = new LearningEvent();
        event.userId = userId;
        event.type = type;
        return event;
    }

    public LearningEvent ref(String refType, Long refId) {
        this.refType = refType;
        this.refId = refId;
        return this;
    }

    public LearningEvent point(String knowledgePoint) {
        this.knowledgePoint = knowledgePoint;
        return this;
    }

    public LearningEvent score(Double score) {
        this.score = score;
        return this;
    }

    public LearningEvent minutes(int minutes) {
        this.minutes = minutes;
        return this;
    }

    public LearningEvent detail(String detail) {
        this.detail = detail != null && detail.length() > 250 ? detail.substring(0, 250) : detail;
        return this;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public String getKnowledgePoint() {
        return knowledgePoint;
    }

    public Double getScore() {
        return score;
    }

    public int getMinutes() {
        return minutes;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
