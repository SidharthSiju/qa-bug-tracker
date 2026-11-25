package com.duc.crqa.attachment.service;

import com.duc.crqa.bug.entity.Bug;
import com.duc.crqa.bug.entity.BugAttachment;
import com.duc.crqa.bug.repository.BugAttachmentRepository;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "pdf", "txt", "csv", "xls", "xlsx", "doc", "docx", "zip", "mp4");

    private final BugAttachmentRepository bugAttachmentRepository;

    @Value("${app.storage.location:./uploads}")
    private String storageLocation;

    @Value("${app.storage.max-file-size:20971520}")
    private long maxFileSize;

    public AttachmentStorageService(BugAttachmentRepository bugAttachmentRepository) {
        this.bugAttachmentRepository = bugAttachmentRepository;
    }

    @Transactional
    public BugAttachment store(MultipartFile file, Bug bug, Long uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Attachment file is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("Attachment exceeds maximum allowed size");
        }

        String originalName = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type is not allowed: " + extension);
        }

        String storedName = UUID.randomUUID() + "_" + originalName;
        Path directory = Paths.get(storageLocation, "bug-" + bug.getId()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(storedName).normalize();
            if (!target.startsWith(directory)) {
                throw new IllegalArgumentException("Invalid file path");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            BugAttachment attachment = new BugAttachment();
            attachment.setBug(bug);
            attachment.setFileName(originalName);
            attachment.setContentType(file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setStorageType("LOCAL_FILESYSTEM");
            attachment.setStoragePath(target.toString());
            attachment.setUploadedBy(uploadedBy);
            return bugAttachmentRepository.save(attachment);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store attachment", ex);
        }
    }

    /** Best-effort removal of a bug's attachment files (the whole {@code bug-<id>} directory). */
    public void deleteForBug(Long bugId) {
        Path directory = Paths.get(storageLocation, "bug-" + bugId).toAbsolutePath().normalize();
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup; the DB rows are already gone
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    public Resource load(BugAttachment attachment) {
        Path path = Paths.get(attachment.getStoragePath()).toAbsolutePath().normalize();
        try {
            InputStream stream = Files.newInputStream(path);
            return new InputStreamResource(stream);
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Attachment file not found");
        }
    }

    private String sanitizeFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment";
        }
        String base = Paths.get(fileName).getFileName().toString();
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
