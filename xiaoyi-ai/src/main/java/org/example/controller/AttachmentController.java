package org.example.controller;

import org.example.service.AttachmentStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/chat/attachments")
public class AttachmentController {

    private final AttachmentStorageService attachmentStorageService;

    public AttachmentController(AttachmentStorageService attachmentStorageService) {
        this.attachmentStorageService = attachmentStorageService;
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<Resource> getAttachment(@PathVariable Long attachmentId) {
        AttachmentStorageService.StoredAttachment storedAttachment = attachmentStorageService.load(attachmentId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(storedAttachment.attachment().getFileType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(storedAttachment.attachment().getOriginalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(new FileSystemResource(storedAttachment.path()));
    }
}