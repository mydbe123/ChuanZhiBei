package com.zhixue.engine.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "zx_messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private long conversationId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String reasoning;

    @Column(name = "tool_trace", columnDefinition = "MEDIUMTEXT")
    private String toolTrace;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String citations;

    @Column(nullable = false)
    private int tokens;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public static Message of(long conversationId, String role, String content) {
        Message message = new Message();
        message.conversationId = conversationId;
        message.role = role;
        message.content = content;
        return message;
    }

    public Long getId() {
        return id;
    }

    public long getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getReasoning() {
        return reasoning;
    }

    public Message setReasoning(String reasoning) {
        this.reasoning = reasoning;
        return this;
    }

    public String getToolTrace() {
        return toolTrace;
    }

    public Message setToolTrace(String toolTrace) {
        this.toolTrace = toolTrace;
        return this;
    }

    public String getCitations() {
        return citations;
    }

    public Message setCitations(String citations) {
        this.citations = citations;
        return this;
    }

    public int getTokens() {
        return tokens;
    }

    public Message setTokens(int tokens) {
        this.tokens = tokens;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 供演示数据导入使用，可指定历史时间 */
    public Message at(LocalDateTime time) {
        this.createdAt = time;
        return this;
    }
}
