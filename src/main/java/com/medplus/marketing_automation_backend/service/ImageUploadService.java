package com.medplus.marketing_automation_backend.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles asset uploads to the company image server via a three-step flow:
 *  1. Obtain a short-lived OAuth token (client-credentials grant).
 *  2. Fetch image-server URL + upload token from the transit API.
 *  3. POST each file as multipart/form-data and collect the resulting paths.
 *
 * The full public URL for each asset is:  imageServerUrl + "/" + imagePath
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
    private static final String ORIGIN    = "rnd_internal";
    private static final String CLIENT_ID = "medplus_rnd_app";

    // ── Step 3 — Upload ────────────────────────────────────────────────────────
    private static final String IMAGE_TYPE = "LT";

    private final RestTemplate restTemplate;

    public ImageUploadService() {
        this.restTemplate = buildRestTemplate();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Uploads every file in the list and returns the full public URL for each one.
     * Files are processed sequentially; the OAuth + server-details calls are made
     * once per batch (not once per file) to avoid unnecessary round-trips.
     */
    public List<String> uploadFiles(List<MultipartFile> files) {
        String oauthToken           = fetchOAuthToken();
        ImageServerDetails details  = fetchImageServerDetails(oauthToken);

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            String imagePath = uploadSingleFile(file, details);
            urls.add(details.imageServerUrl + "/" + imagePath);
        }
        return urls;
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
    private String uploadSingleFile(MultipartFile file, ImageServerDetails details) {
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

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                uploadUrl, HttpMethod.POST, new HttpEntity<>(body, headers), responseType());

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("Empty response from image-server upload");
        }

        // The API wraps the result in a nested "response" array:
        // { "statusCode": "SUCCESS", "response": [{ "imagePath": "...", ... }] }
        Object responseValue = responseBody.get("response");
        Map<String, Object> fileResult;
        if (responseValue instanceof java.util.List<?> list && !list.isEmpty()) {
            fileResult = (Map<String, Object>) list.get(0);
        } else if (responseValue instanceof Map) {
            fileResult = (Map<String, Object>) responseValue;
        } else {
            // Fallback: maybe imagePath is at the top level
            fileResult = responseBody;
        }

        String imagePath = (String) fileResult.get("imagePath");
        if (imagePath == null) {
            throw new RuntimeException("Unexpected response from image-server upload: " + responseBody);
        }
        return imagePath;
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
            factory.setReadTimeout(60_000);

            return new RestTemplate(factory);
        } catch (java.security.NoSuchAlgorithmException | java.security.KeyManagementException e) {
            throw new ExceptionInInitializerError("Could not configure image-upload RestTemplate: " + e.getMessage());
        }
    }
}
