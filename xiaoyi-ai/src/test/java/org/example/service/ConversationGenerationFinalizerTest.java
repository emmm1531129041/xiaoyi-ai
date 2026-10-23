package org.example.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ConversationGenerationFinalizerTest {

    @Test
    void releasesWaitingReplacementAfterSuccessfulMemoryReload() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        ConversationGenerationCoordinator coordinator = new ConversationGenerationCoordinator();
        ConversationGenerationFinalizer finalizer = new ConversationGenerationFinalizer(memoryService, coordinator);
        ConversationGenerationCoordinator.Generation first = coordinator.begin("conversation-1");
        ConversationGenerationCoordinator.Generation replacement = coordinator.begin("conversation-1");

        finalizer.finish("conversation-1", first);
        replacement.awaitPreviousTermination().block(Duration.ofSeconds(1));

        verify(memoryService).reload("conversation-1");
        coordinator.finish(replacement);
    }

    @Test
    void releasesWaitingReplacementWhenMemoryReloadFails() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        ConversationGenerationCoordinator coordinator = new ConversationGenerationCoordinator();
        ConversationGenerationFinalizer finalizer = new ConversationGenerationFinalizer(memoryService, coordinator);
        ConversationGenerationCoordinator.Generation first = coordinator.begin("conversation-1");
        ConversationGenerationCoordinator.Generation replacement = coordinator.begin("conversation-1");
        doThrow(new IllegalStateException("database unavailable"))
                .when(memoryService).reload("conversation-1");

        assertDoesNotThrow(() -> finalizer.finish("conversation-1", first));
        replacement.awaitPreviousTermination().block(Duration.ofSeconds(1));

        verify(memoryService).reload("conversation-1");
        coordinator.finish(replacement);
    }
}
