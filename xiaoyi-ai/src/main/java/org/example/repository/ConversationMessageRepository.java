package org.example.repository;

import org.example.entity.ConversationMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, Long> {

    List<ConversationMessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    boolean existsByConversationId(String conversationId);

    long deleteByConversationId(String conversationId);
}