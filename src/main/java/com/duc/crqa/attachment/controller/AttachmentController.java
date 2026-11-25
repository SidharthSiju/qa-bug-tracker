package com.duc.crqa.attachment.controller;

import com.duc.crqa.attachment.service.AttachmentStorageService;
import com.duc.crqa.bug.entity.BugAttachment;
import com.duc.crqa.bug.repository.BugAttachmentRepository;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class AttachmentController {

    private final BugAttachmentRepository bugAttachmentRepository;
    private final AttachmentStorageService attachmentStorageService;

    public AttachmentController(BugAttachmentRepository bugAttachmentRepository,
                                AttachmentStorageService attachmentStorageService) {
        this.bugAttachmentRepository = bugAttachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        BugAttachment attachment = bugAttachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + id));
        Resource resource = attachmentStorageService.load(attachment);
        String contentType = attachment.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : attachment.getContentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getFileName() + "\"")
                .body(resource);
    }
}
