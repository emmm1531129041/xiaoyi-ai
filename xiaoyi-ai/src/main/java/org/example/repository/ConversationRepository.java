package org.example.repository;

import org.example.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    List<ConversationEntity> findAllByOrderByUpdatedAtDesc();
}