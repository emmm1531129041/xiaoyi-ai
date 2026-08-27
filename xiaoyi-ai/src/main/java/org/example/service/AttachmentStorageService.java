package org.example.service;

import jakarta.annotation.PostConstruct;
import org.example.entity.ConversationAttachmentEntity;
import org.example.repository.ConversationAttachmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Service
public class AttachmentStorageService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorageService.class);

    private final ConversationAttachmentRepository attachmentRepository;
    private final Path uploadDirectory;

    public AttachmentStorageService(ConversationAttachmentRepository attachmentRepository,
                                    @Value("${xiaoyi.storage.upload-dir}") String uploadDirectory) {
        this.attachmentRepository = attachmentRepository;
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initializeStorageDirectory() {
        try {
            Files.createDirectories(uploadDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建附件存储目录：" + uploadDirectory, exception);
        }
    }

    public ConversationAttachmentEntity store(String conversationId, Long messageId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传附件不能为空");
        }

        Files.createDirectories(uploadDirectory);
        String originalName = safeOriginalName(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + extensionOf(originalName);
        Path target = uploadDirectory.resolve(storedName).normalize();
        verifyInsideUploadDirectory(target);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }

        try {
            return attachmentRepository.save(new ConversationAttachmentEntity(
                    conversationId, messageId, originalName, storedName, target.toString(),
                    StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream",
                    file.getSize(), Instant.now()));
        } catch (RuntimeException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    public void deleteStoredFiles(Collection<ConversationAttachmentEntity> attachments) {
        for (ConversationAttachmentEntity attachment : attachments) {
            try {
                Path file = Paths.get(attachment.getFilePath()).toAbsolutePath().normalize();
                verifyInsideUploadDirectory(file);
                if (Files.deleteIfExists(file)) {
                    log.info("已删除会话附件文件：{}", file);
                }
            } catch (IOException exception) {
                log.warn("删除会话附件文件失败，仍将继续删除数据库记录：{}", attachment.getFilePath(), exception);
            } catch (IllegalArgumentException exception) {
                log.warn("拒绝删除存储目录外的附件路径：{}", attachment.getFilePath());
            }
        }
    }

    public StoredAttachment load(Long attachmentId) {
        ConversationAttachmentEntity attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));
        Path file = Paths.get(attachment.getFilePath()).toAbsolutePath().normalize();
        try {
            verifyInsideUploadDirectory(file);
            if (!Files.isRegularFile(file)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件文件不存在");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件文件不存在");
        }
        return new StoredAttachment(attachment, file);
    }

    private String safeOriginalName(String originalFilename) {
        String cleaned = StringUtils.cleanPath(originalFilename == null ? "attachment" : originalFilename)
                .replace('\\', '/');
        int slashIndex = cleaned.lastIndexOf('/');
        String filename = slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned;
        return StringUtils.hasText(filename) ? filename : "attachment";
    }

    private String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dotIndex).replaceAll("[^A-Za-z0-9.]", "");
        return extension.length() <= 16 ? extension : "";
    }

    private void verifyInsideUploadDirectory(Path path) {
        if (!path.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException("附件路径不在允许的存储目录内");
        }
    }

    public record StoredAttachment(ConversationAttachmentEntity attachment, Path path) {
    }
}