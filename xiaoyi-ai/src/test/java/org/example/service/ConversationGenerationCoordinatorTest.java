package org.example.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ConversationGenerationCoordinatorTest {

    @Test
    void newerGenerationCancelsThePreviousGenerationAndWaitsForItsCleanup() {
        ConversationGenerationCoordinator coordinator = new ConversationGenerationCoordinator();

        ConversationGenerationCoordinator.Generation first = coordinator.begin("conversation-1");
        ConversationGenerationCoordinator.Generation second = coordinator.begin("conversation-1");

        assertTrue(first.isCancelled());
        coordinator.finish(first);
        second.awaitPreviousTermination().block(Duration.ofSeconds(1));

        ConversationGenerationCoordinator.Generation third = coordinator.begin("conversation-1");
        assertTrue(second.isCancelled());

        coordinator.finish(second);
        coordinator.finish(third);
    }

    @Test
    void finishingAnOldGenerationDoesNotReleaseItsReplacement() {
        ConversationGenerationCoordinator coordinator = new ConversationGenerationCoordinator();
        ConversationGenerationCoordinator.Generation first = coordinator.begin("conversation-1");
        ConversationGenerationCoordinator.Generation second = coordinator.begin("conversation-1");

        coordinator.finish(first);
        ConversationGenerationCoordinator.Generation third = coordinator.begin("conversation-1");

        assertTrue(second.isCancelled());
        coordinator.finish(second);
        coordinator.finish(third);
    }

    @Test
    void cancellationCanBeAwaitedBeforeDeletingAConversation() {
        ConversationGenerationCoordinator coordinator = new ConversationGenerationCoordinator();
        ConversationGenerationCoordinator.Generation generation = coordinator.begin("conversation-1");

        var stopped = coordinator.cancelAndAwait("conversation-1");
        assertTrue(generation.isCancelled());
        coordinator.finish(generation);

        stopped.block(Duration.ofSeconds(1));
    }
}
