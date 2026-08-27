package org.example.service;

import org.example.entity.ConversationAttachmentEntity;
import org.example.repository.ConversationAttachmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesWithUuidNameAndToleratesMissingFileDuringCleanup() throws Exception {
        ConversationAttachmentRepository repository = mock(ConversationAttachmentRepository.class);
        when(repository.save(any(ConversationAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AttachmentStorageService service = new AttachmentStorageService(repository, temporaryDirectory.toString());
        service.initializeStorageDirectory();

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
        ConversationAttachmentEntity attachment = service.store("conversation-1", 10L, file);
        Path storedFile = Path.of(attachment.getFilePath());

        assertTrue(Files.isRegularFile(storedFile));
        assertEquals("photo.png", attachment.getOriginalName());
        assertTrue(attachment.getStoredName().endsWith(".png"));
        assertEquals(3, attachment.getFileSize());

        Files.delete(storedFile);
        assertDoesNotThrow(() -> service.deleteStoredFiles(List.of(attachment)));
    }
}