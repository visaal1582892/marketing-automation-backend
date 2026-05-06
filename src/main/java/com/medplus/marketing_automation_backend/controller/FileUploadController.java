package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.exception.UnsupportedFileFormatException;
import com.medplus.marketing_automation_backend.service.ImageUploadService;
import com.medplus.marketing_automation_backend.service.ImageUploadService.UploadResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 *   Each file is uploaded to the external MedPlus image server independently.
 *   If some files succeed and others fail, the response still returns HTTP 200
 *   with per-file result information so the caller can handle partial success.
 *   No files are stored locally.
 *
 * <p>Response shape (single-file call from frontend):
 * <pre>
 * {
 *   "urls":              ["https://server/path/to/file"],
 *   "thumbnailUrls":     ["https://server/path/to/thumb"],
 *   "originalFilenames": ["report.pdf"]
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

        // Upload each file independently — one failure must not cancel the rest.
        List<String> urls              = new ArrayList<>();
        List<String> thumbnailUrls     = new ArrayList<>();
        List<String> originalFilenames = new ArrayList<>();
        List<String> errors            = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                UploadResult result = imageUploadService.uploadSingleFilePublic(file);
                urls.add(result.url());
                thumbnailUrls.add(result.thumbnailUrl());
                originalFilenames.add(file.getOriginalFilename());
                errors.add(null);
            } catch (UnsupportedFileFormatException ex) {
                urls.add(null);
                thumbnailUrls.add(null);
                originalFilenames.add(file.getOriginalFilename());
                errors.add(ex.getMessage());
            } catch (Exception ex) {
                urls.add(null);
                thumbnailUrls.add(null);
                originalFilenames.add(file.getOriginalFilename());
                String msg = ex.getMessage() != null ? ex.getMessage() : "Upload failed";
                // Friendly message for timeouts
                String friendlyMsg = msg.contains("Read timed out") || msg.contains("timed out")
                        ? "Upload timed out — file may not be supported by the image server"
                        : "Upload failed: " + msg;
                errors.add(friendlyMsg);
            }
        }

        // If every file failed, surface the first error as a top-level 400/500
        boolean allFailed = urls.stream().allMatch(u -> u == null);
        if (allFailed) {
            String firstError = errors.stream().filter(e -> e != null).findFirst()
                    .orElse("All files failed to upload");
            boolean isFormatError = firstError.toLowerCase().contains("unsupported")
                    || firstError.toLowerCase().contains("format")
                    || firstError.toLowerCase().contains("timed out");
            return isFormatError
                    ? ResponseEntity.badRequest().body(Map.of("message", firstError))
                    : ResponseEntity.internalServerError().body(Map.of("message", firstError));
        }

        // Partial or full success — return per-file results so the frontend can show
        // individual file statuses.
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("urls",              urls);
        response.put("thumbnailUrls",     thumbnailUrls);
        response.put("originalFilenames", originalFilenames);
        response.put("errors",            errors);
        return ResponseEntity.ok(response);
    }

    /**
     * Proxies a file from the external image server through the backend so the
     * browser receives it with Content-Disposition: attachment (forced download).
     * This is necessary because the image server is cross-origin and does not
     * emit CORS headers that would allow browser-side blob fetching.
     *
     * <p>GET /api/upload/proxy-download?url=https://image-server/path/to/file
     */
    @GetMapping("/proxy-download")
    public ResponseEntity<byte[]> proxyDownload(@RequestParam("url") String url) {
        if (url == null || url.isBlank()
                || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] bytes = imageUploadService.downloadFile(url);

            String filename = url.contains("/")
                    ? url.substring(url.lastIndexOf('/') + 1) : "file";
            if (filename.contains("?")) filename = filename.substring(0, filename.indexOf('?'));
            if (filename.isBlank()) filename = "file";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
