package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.exception.UnsupportedFileFormatException;
import com.medplus.marketing_automation_backend.service.ImageUploadService;
import com.medplus.marketing_automation_backend.service.ImageUploadService.UploadResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles asset uploads for task submissions and collaborations.
 *
 * <p>POST /api/upload/asset
 *   All files (images, documents, videos, etc.) are uploaded to the external
 *   MedPlus image server via a 3-step OAuth + transit API flow.
 *   No files are stored locally.
 *
 * <p>Response shape:
 * <pre>
 * {
 *   "urls":              ["https://server/path/to/file", ...],
 *   "thumbnailUrls":     ["https://server/path/to/thumb", ...],  // may contain nulls
 *   "originalFilenames": ["report.docx", ...]
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    private final ImageUploadService imageUploadService;

    public FileUploadController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping("/asset")
    public ResponseEntity<Map<String, Object>> uploadAssets(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "No files provided"));
        }

        List<UploadResult> results;
        try {
            results = imageUploadService.uploadFiles(files);
        } catch (UnsupportedFileFormatException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Upload failed: " + ex.getMessage()));
        }

        List<String> urls              = new ArrayList<>();
        List<String> thumbnailUrls     = new ArrayList<>();
        List<String> originalFilenames = new ArrayList<>();

        for (UploadResult r : results) {
            urls.add(r.url());
            thumbnailUrls.add(r.thumbnailUrl());
            originalFilenames.add(r.originalImageName());
        }

        return ResponseEntity.ok(Map.of(
                "urls",              urls,
                "thumbnailUrls",     thumbnailUrls,
                "originalFilenames", originalFilenames
        ));
    }
}
