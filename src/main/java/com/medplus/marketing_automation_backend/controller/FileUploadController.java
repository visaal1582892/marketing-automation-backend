package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.service.ImageUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Handles asset uploads for task submissions.
 *
 * POST /api/upload/asset
 *   Accepts one or more files (multipart/form-data, key = "files").
 *   Proxies each file to the company image server via the three-step
 *   OAuth → server-details → upload flow, then returns the full public
 *   URLs for the caller to attach to the task submission.
 *
 * Accessible to any authenticated user (worker role uploads assets during
 * task execution; security is enforced by the JWT filter).
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

        List<String> urls = imageUploadService.uploadFiles(files);
        return ResponseEntity.ok(Map.of("urls", urls));
    }
}
