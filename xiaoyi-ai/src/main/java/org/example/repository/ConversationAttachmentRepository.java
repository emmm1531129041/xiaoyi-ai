package org.example.repository;

import org.example.entity.ConversationAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ConversationAttachmentRepository extends JpaRepository<ConversationAttachmentEntity, Long> {

    List<ConversationAttachmentEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<ConversationAttachmentEntity> findByMessageIdIn(Collection<Long> messageIds);
}