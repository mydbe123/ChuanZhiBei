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
        createdAt = LocalDateTime.now();
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

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getToolTrace() {
        return toolTrace;
    }

    public void setToolTrace(String toolTrace) {
        this.toolTrace = toolTrace;
    }

    public String getCitations() {
        return citations;
    }

    public void setCitations(String citations) {
        this.citations = citations;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
