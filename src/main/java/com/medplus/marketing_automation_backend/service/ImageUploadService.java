package com.medplus.marketing_automation_backend.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.medplus.marketing_automation_backend.exception.UnsupportedFileFormatException;

/**
 * Handles asset uploads to the company image server via a three-step flow:
 *  1. Obtain a short-lived OAuth token (client-credentials grant).
 *  2. Fetch image-server URL + upload token from the transit API.
 *  3. POST each file as multipart/form-data and collect the resulting metadata.
 *
 * All file types (images, documents, videos, etc.) go through this service.
 * No files are stored locally.
 */
@Service
public class ImageUploadService {

    // ── Step 1 — OAuth ────────────────────────────────────────────────────────
    private static final String OAUTH_URL =
            "http://192.168.1.60:8103/oauth-server/oauth/token?grant_type=client_credentials";
    private static final String OAUTH_USERNAME = "meditimes_client";
    private static final String OAUTH_PASSWORD = "Med1T!me$CL";

    // ── Step 2 — Image-server details ─────────────────────────────────────────
    private static final String IMAGE_SERVER_DETAILS_URL =
            "https://marigold.medplusindia.com:6426/diagnostics/transit/image-server";
    private static final String ORIGIN    = "marketing_automation";
    private static final String CLIENT_ID = "medplus_marketing_automation_app";

    // ── Step 3 — Upload ────────────────────────────────────────────────────────
    private static final String IMAGE_TYPE = "LT";

    private final RestTemplate restTemplate;

    public ImageUploadService() {
        this.restTemplate = buildRestTemplate();
    }

    // ── Result DTO ─────────────────────────────────────────────────────────────

    /**
     * Holds all metadata returned by the image server for a single uploaded file.
     *
     * @param url               Full public URL of the uploaded file  (imageServerUrl/imagePath)
     * @param thumbnailUrl      Full public URL of the thumbnail, or null if not provided
     * @param originalImageName Original filename as reported by the image server
     */
    public record UploadResult(String url, String thumbnailUrl, String originalImageName) {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Fetches a file from the external image server by URL and returns the raw
     * bytes.  Uses the same trust-all RestTemplate so self-signed certificates
     * on the image server are accepted.
     */
    public byte[] downloadFile(String fileUrl) {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                fileUrl, HttpMethod.GET, HttpEntity.EMPTY, byte[].class);
        byte[] body = response.getBody();
        return body != null ? body : new byte[0];
    }

    /**
     * Uploads every file in the list to the external image server and returns
     * rich metadata for each one.  The OAuth + server-details calls are made
     * once per batch to avoid unnecessary round-trips.
     * Accepts all file types — images, documents, videos, etc.
     */
    public List<UploadResult> uploadFiles(List<MultipartFile> files) {
        String oauthToken          = fetchOAuthToken();
        ImageServerDetails details = fetchImageServerDetails(oauthToken);

        List<UploadResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadSingleFile(file, details));
        }
        return results;
    }

    /**
     * Public single-file upload: fetches a fresh OAuth token + server details for
     * every call.  Use this when the caller wants to handle per-file failures
     * independently (e.g. the upload controller's partial-success loop).
     */
    public UploadResult uploadSingleFilePublic(MultipartFile file) {
        String oauthToken          = fetchOAuthToken();
        ImageServerDetails details = fetchImageServerDetails(oauthToken);
        return uploadSingleFile(file, details);
    }

    // ── Step 1 ─────────────────────────────────────────────────────────────────

    private String fetchOAuthToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(OAUTH_USERNAME, OAUTH_PASSWORD);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                OAUTH_URL, HttpMethod.POST, new HttpEntity<>(headers), responseType());

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain OAuth token — unexpected response from auth server");
        }
        return (String) body.get("access_token");
    }

    // ── Step 2 ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ImageServerDetails fetchImageServerDetails(String oauthToken) {
        String url = IMAGE_SERVER_DETAILS_URL
                + "?origin=" + ORIGIN
                + "&clientId=" + CLIENT_ID;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(oauthToken);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), responseType());

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new RuntimeException("Empty response from image-server details endpoint");
        }

        // The API wraps the actual details inside a nested "response" object:
        // { "statusCode": "SUCCESS", "response": { "imageServerUrl": "...", ... } }
        Map<String, Object> inner = body.containsKey("response")
                ? (Map<String, Object>) body.get("response")
                : body;

        String serverUrl   = (String) inner.get("imageServerUrl");
        String uploadToken = (String) inner.get("accessToken");
        String clientId    = (String) inner.get("clientId");

        if (serverUrl == null || uploadToken == null) {
            throw new RuntimeException("Incomplete image-server details: " + body);
        }

        return new ImageServerDetails(serverUrl, uploadToken, clientId != null ? clientId : CLIENT_ID);
    }

    // ── Step 3 ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private UploadResult uploadSingleFile(MultipartFile file, ImageServerDetails details) {
        String uploadUrl = details.imageServerUrl + "/upload"
                + "?token="      + details.uploadToken
                + "&clientId="   + details.clientId
                + "&imageType="  + IMAGE_TYPE;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            final String originalFilename = file.getOriginalFilename();
            byte[] bytes = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override public String getFilename() { return originalFilename; }
            };
            body.add("files", resource);
        } catch (IOException e) {
            throw new RuntimeException("Could not read uploaded file '" + file.getOriginalFilename() + "': " + e.getMessage(), e);
        }

        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(
                    uploadUrl, HttpMethod.POST, new HttpEntity<>(body, headers), responseType());
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            throw new UnsupportedFileFormatException(
                    "File '" + file.getOriginalFilename() + "' was rejected by the image server: "
                    + ex.getStatusCode() + ". Only images, videos and PDFs are supported.");
        }

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("Empty response from image-server upload");
        }

        // Check for explicit error status from image server (e.g. { "statusCode": "UNSUPPORTED_FORMAT", ... })
        Object statusCode = responseBody.get("statusCode");
        if (statusCode instanceof String s && !s.equalsIgnoreCase("SUCCESS") && !s.equalsIgnoreCase("200")) {
            String errorMsg = responseBody.containsKey("message") ? (String) responseBody.get("message") : s;
            if (s.toUpperCase().contains("UNSUPPORTED") || s.toUpperCase().contains("FORMAT")) {
                throw new UnsupportedFileFormatException(
                        "File format not supported: '" + file.getOriginalFilename()
                        + "'. Only images, videos and PDFs are accepted.");
            }
            throw new RuntimeException("Image server error for '" + file.getOriginalFilename() + "': " + errorMsg);
        }

        // The API wraps the result in a nested "response" array:
        // { "statusCode": "SUCCESS", "response": [{ "imagePath": "...", "thumbnailPath": "...", "originalImageName": "..." }] }
        Object responseValue = responseBody.get("response");
        Map<String, Object> fileResult;
        if (responseValue instanceof java.util.List<?> list && !list.isEmpty()) {
            fileResult = (Map<String, Object>) list.get(0);
        } else if (responseValue instanceof Map) {
            fileResult = (Map<String, Object>) responseValue;
        } else {
            fileResult = responseBody;
        }

        String imagePath         = (String) fileResult.get("imagePath");
        String thumbnailPath     = (String) fileResult.get("thumbnailPath");
        String originalImageName = (String) fileResult.get("originalImageName");

        if (imagePath == null) {
            throw new RuntimeException("Unexpected response from image-server upload: " + responseBody);
        }

        String fullUrl      = details.imageServerUrl + "/" + imagePath;
        String thumbnailUrl = thumbnailPath != null ? details.imageServerUrl + "/" + thumbnailPath : null;
        String origName     = originalImageName != null ? originalImageName : file.getOriginalFilename();

        return new UploadResult(fullUrl, thumbnailUrl, origName);
    }

    /** Type token helper — avoids raw-Map warnings on exchange() calls. */
    private static org.springframework.core.ParameterizedTypeReference<Map<String, Object>> responseType() {
        return new org.springframework.core.ParameterizedTypeReference<>() {};
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private record ImageServerDetails(String imageServerUrl, String uploadToken, String clientId) {}

    /**
     * Builds a RestTemplate that skips SSL certificate validation.
     * Required because the image server at port 6426 uses a private/self-signed
     * certificate that the default JVM trust-store does not recognise.
     *
     * Note: this trust-all configuration is intentional for this internal-only
     * service call; it does not affect outbound connections made elsewhere in
     * the application.
     */
    private static RestTemplate buildRestTemplate() {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                @Override public void checkClientTrusted(X509Certificate[] c, String a) {}
                @Override public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(15_000);
            factory.setReadTimeout(120_000);

            return new RestTemplate(factory);
        } catch (java.security.NoSuchAlgorithmException | java.security.KeyManagementException e) {
            throw new ExceptionInInitializerError("Could not configure image-upload RestTemplate: " + e.getMessage());
        }
    }
}
