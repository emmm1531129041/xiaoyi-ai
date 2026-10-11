package org.example.service;

import org.example.repository.*;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(ConversationService.class)
class ConversationTransactionIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMessageRepository messageRepository;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private AttachmentStorageService attachmentStorageService;

    @MockitoBean
    private ChatMemory chatMemory;

    @MockitoBean
    private ConversationMemoryService conversationMemoryService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollbackWhenAttachmentFails() throws Exception {

        // 确保没有误连正常业务数据库
        try (var connection = dataSource.getConnection()) {
            assertEquals("xiaoyi_ai_test", connection.getCatalog());
        }

        String conversationId = UUID.randomUUID().toString();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", new byte[]{1, 2, 3}
        );

        // 模拟附件存储失败
        when(attachmentStorageService.store(
                eq(conversationId), anyLong(), any()
        )).thenThrow(new IOException("模拟附件保存失败"));

        // 验证异常确实抛出
        assertThrows(IOException.class, () ->
                conversationService.addUserMessageWithAttachments(
                        conversationId, "测试事务回滚", List.of(file)
                )
        );

        // 验证 MySQL 中的会话和消息均未保留
        assertFalse(conversationRepository.existsById(conversationId));
        assertFalse(messageRepository.existsByConversationId(conversationId));
    }
}
