package com.zhixue.engine.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record ConversationView(long id, String title, String scene, int messageCount,
                                   LocalDateTime updatedAt) {
        public static ConversationView of(Conversation conversation) {
            return new ConversationView(conversation.getId(), conversation.getTitle(),
                    conversation.getScene(), conversation.getMessageCount(), conversation.getUpdatedAt());
        }
    }

    public record MessageView(long id, String role, String content, String reasoning,
                              List<Map<String, Object>> toolTrace, List<Map<String, Object>> citations,
                              LocalDateTime createdAt) {
    }

    public record CreateConversationRequest(String title, String scene) {
    }

    public record ChatRequest(Long conversationId, String content, List<String> images, Boolean useKnowledge) {
    }
}
