package org.example.service;

import org.example.entity.ConversationMessageEntity;
import org.example.repository.ConversationMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryServiceTest {

    @Test
    void restoresTheLatestMessagesInChronologicalOrderOnlyOnce() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);
        Instant earlier = Instant.parse("2026-09-02T08:00:00Z");
        Instant later = Instant.parse("2026-09-02T08:01:00Z");
        when(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc("conversation-1"))
                .thenReturn(List.of(
                        new ConversationMessageEntity("conversation-1", "assistant", "你好", later),
                        new ConversationMessageEntity("conversation-1", "user", "我叫张童", earlier)));

        ConversationMemoryService service = new ConversationMemoryService(chatMemory, messageRepository);
        service.restoreIfNeeded("conversation-1");
        service.restoreIfNeeded("conversation-1");

        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatMemory).clear("conversation-1");
        verify(chatMemory).add(eq("conversation-1"), messages.capture());
        verify(messageRepository, times(1)).findTop10ByConversationIdOrderByCreatedAtDesc("conversation-1");
        assertInstanceOf(UserMessage.class, messages.getValue().get(0));
        assertInstanceOf(AssistantMessage.class, messages.getValue().get(1));
        assertEquals("我叫张童", ((UserMessage) messages.getValue().get(0)).getText());
    }

    @Test
    void reloadReplacesRuntimeMemoryWithPersistedMessages() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);
        when(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc("conversation-1"))
                .thenReturn(List.of(new ConversationMessageEntity(
                        "conversation-1", "user", "新的调整要求", Instant.parse("2026-10-23T08:00:00Z"))));

        ConversationMemoryService service = new ConversationMemoryService(chatMemory, messageRepository);
        service.reload("conversation-1");

        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatMemory).clear("conversation-1");
        verify(chatMemory).add(eq("conversation-1"), messages.capture());
        assertEquals("新的调整要求", ((UserMessage) messages.getValue().get(0)).getText());
    }
}
