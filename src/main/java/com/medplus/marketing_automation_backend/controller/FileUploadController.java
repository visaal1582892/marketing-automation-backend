package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.service.ImageUploadService;
import com.medplus.marketing_automation_backend.service.LocalFileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles asset uploads for task submissions and collaborations.
 *
 * <p>POST /api/upload/asset
 *   Routes each file to the appropriate storage back-end:
 *   <ul>
 *     <li>Images (.jpg/.png/etc.) → external MedPlus image server</li>
 *     <li>Documents (.doc/.docx/.xls/.xlsx/.ppt/.pptx/.pdf) and videos
 *         → local filesystem, served via GET /api/upload/files/{filename}</li>
 *   </ul>
 *
 * <p>GET /api/upload/files/{filename}
 *   Streams a locally stored file back to the client. This endpoint is
 *   public (no JWT) so that browser {@code <img>} / {@code <a>} tags can
 *   access uploaded documents without additional auth headers.
 */
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    private final ImageUploadService      imageUploadService;
    private final LocalFileStorageService localStorage;

    public FileUploadController(ImageUploadService imageUploadService,
                                LocalFileStorageService localStorage) {
        this.imageUploadService = imageUploadService;
        this.localStorage       = localStorage;
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping("/asset")
    public ResponseEntity<Map<String, Object>> uploadAssets(
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "No files provided"));
        }

        String baseUrl = resolveBaseUrl(request);
        List<String> urls = new ArrayList<>();

        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            if (LocalFileStorageService.isImageServerSupported(name)) {
                // Images → external MedPlus image server
                urls.addAll(imageUploadService.uploadFiles(List.of(file)));
            } else {
                // Documents / videos / other → local filesystem
                String storedName = localStorage.store(file);
                urls.add(baseUrl + "/api/upload/files/" + storedName);
            }
        }

        return ResponseEntity.ok(Map.of("urls", urls));
    }

    // ── Serve locally stored files ────────────────────────────────────────────

    /**
     * Streams a locally stored file. No authentication required so that
     * browser-rendered links and download anchors work without custom headers.
     */
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Resource resource = localStorage.loadAsResource(filename);

        // Guess MIME type from the filename; fall back to binary stream
        String contentType = URLConnection.guessContentTypeFromName(filename);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Constructs the server's public base URL, honouring ngrok / reverse-proxy
     * forwarding headers so that returned document URLs work from the browser.
     */
    private static String resolveBaseUrl(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) proto = request.getScheme();

        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = request.getHeader("Host");
        if (host == null || host.isBlank())
            host = request.getServerName() + ":" + request.getServerPort();

        return proto + "://" + host;
    }
}
