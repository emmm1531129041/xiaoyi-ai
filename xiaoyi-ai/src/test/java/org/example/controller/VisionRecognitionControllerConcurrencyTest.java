package org.example.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.service.ConversationGenerationCoordinator;
import org.example.service.ConversationGenerationFinalizer;
import org.example.service.ConversationMemoryService;
import org.example.service.ConversationService;
import org.example.service.VisionRecognitionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import reactor.core.publisher.Flux;

class VisionRecognitionControllerConcurrencyTest {

    @Test
    void aNewAttachmentGenerationCancelsTheActiveGenerationForTheSameConversation() throws IOException {
        VisionRecognitionService visionService = mock(VisionRecognitionService.class);
        when(visionService.stream(anyList(), anyString())).thenReturn(Flux.never());
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        ConversationGenerationCoordinator coordinator = new ConversationGenerationCoordinator();
        VisionRecognitionController controller = new VisionRecognitionController(visionService,
                mock(ConversationService.class), memoryService, coordinator,
                new ConversationGenerationFinalizer(memoryService, coordinator));
        AtomicBoolean completed = new AtomicBoolean();
        List<MultipartFile> files = List.of(mock(MultipartFile.class));

        controller.recognize(files, "识别图片", "conversation-1")
                .subscribe(ignored -> { }, error -> { }, () -> completed.set(true));
        controller.recognize(files, "重新识别", "conversation-1");

        assertTrue(completed.get());
    }
}
