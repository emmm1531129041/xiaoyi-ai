package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Performs non-fatal runtime-memory cleanup before releasing a generation. */
@Service
public class ConversationGenerationFinalizer {

    private static final Logger log = LoggerFactory.getLogger(ConversationGenerationFinalizer.class);

    private final ConversationMemoryService conversationMemoryService;
    private final ConversationGenerationCoordinator generationCoordinator;

    public ConversationGenerationFinalizer(ConversationMemoryService conversationMemoryService,
                                           ConversationGenerationCoordinator generationCoordinator) {
        this.conversationMemoryService = conversationMemoryService;
        this.generationCoordinator = generationCoordinator;
    }

    public void finish(String conversationId, ConversationGenerationCoordinator.Generation generation) {
        try {
            conversationMemoryService.reload(conversationId);
        } catch (RuntimeException exception) {
            // Do not replace a model-stream failure or leave the conversation locked.
            log.warn("Failed to rebuild ChatMemory after generation {} for conversation {}",
                    generation.requestId(), conversationId, exception);
        } finally {
            generationCoordinator.finish(generation);
        }
    }
}
