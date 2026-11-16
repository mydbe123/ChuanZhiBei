package com.zhixue.engine.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(long conversationId);

    void deleteByConversationId(long conversationId);
}
