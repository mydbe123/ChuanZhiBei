package com.zhixue.engine.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(long userId);

    Optional<Conversation> findByIdAndUserId(long id, long userId);
}
