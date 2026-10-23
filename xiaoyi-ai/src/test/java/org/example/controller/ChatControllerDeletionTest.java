package org.example.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.example.service.ConversationGenerationCoordinator;
import org.example.service.ConversationGenerationFinalizer;
import org.example.service.ConversationMemoryService;
import org.example.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class ChatControllerDeletionTest {

    @Test
    void waitsForTheCancelledGenerationBeforeDeletingTheConversation() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        ConversationGenerationCoordinator coordinator = new ConversationGenerationCoordinator();
        ChatController controller = new ChatController(mock(ChatClient.class), conversationService, memoryService,
                coordinator, new ConversationGenerationFinalizer(memoryService, coordinator));
        ConversationGenerationCoordinator.Generation generation = coordinator.begin("conversation-1");

        var deletion = controller.deleteConversation("conversation-1");
        assertTrue(generation.isCancelled());
        coordinator.finish(generation);
        deletion.block();

        verify(conversationService).deleteConversation("conversation-1");
    }
}
