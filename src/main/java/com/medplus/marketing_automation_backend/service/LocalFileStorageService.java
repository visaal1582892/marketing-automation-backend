package com.medplus.marketing_automation_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

/**
 * Stores non-image assets (documents, videos, PDFs) on the local filesystem
 * and serves them back via a public URL.
 *
 * <p>Image files (.jpg, .png, etc.) are handled separately by
 * {@link ImageUploadService} which sends them to the MedPlus image server.
 * Everything else lands here.
 */
@Service
public class LocalFileStorageService {

    /** Extensions routed to the external MedPlus image server. */
    private static final Set<String> IMAGE_SERVER_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg",
            "bmp", "tiff", "tif", "avif", "heic"
    );

    @Value("${app.upload.dir:uploads}")
    private String uploadDirPath;

    private Path uploadDir;

    @PostConstruct
    public void init() throws IOException {
        uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the file should be sent to the MedPlus image
     * server, {@code false} when it should be stored locally.
     */
    public static boolean isImageServerSupported(String filename) {
        if (filename == null || filename.isBlank()) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return false;
        String ext = filename.substring(dot + 1).toLowerCase();
        return IMAGE_SERVER_EXTS.contains(ext);
    }

    /**
     * Saves the uploaded file under a UUID-prefixed name to avoid collisions,
     * preserving the original extension.  Returns the stored filename that
     * should be appended to the serve URL.
     */
    public String store(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext      = "";
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0) ext = original.substring(dot); // e.g. ".docx"
        }

        String storedName = UUID.randomUUID().toString() + ext;
        Path   target     = uploadDir.resolve(storedName);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file '" + original + "': " + e.getMessage(), e);
        }
        return storedName;
    }

    /**
     * Loads a previously stored file as a Spring {@link Resource} for HTTP
     * streaming.  Throws {@link RuntimeException} if the file is not found or
     * unreadable.
     */
    public Resource loadAsResource(String filename) {
        try {
            Path   file     = uploadDir.resolve(filename).normalize();
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("File not found or not readable: " + filename);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid file path: " + filename, e);
        }
    }
}
