package com.medplus.marketing_automation_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * Logs every inbound HTTP request with response status, duration, and — for
 * 4xx/5xx responses — the request body and response error message so issues
 * can be diagnosed directly from the logs without needing a debugger.
 */
@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LOG_SIZE = 2_000; // chars — avoids huge payloads in logs

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();

        String method = request.getMethod();
        String uri    = request.getRequestURI();
        String query  = request.getQueryString();
        String remote = request.getRemoteAddr();
        String target = query != null ? uri + "?" + query : uri;

        // Wrap request/response so we can read bodies after the chain runs.
        // Spring 7 requires an explicit cache-limit on the request wrapper.
        ContentCachingRequestWrapper  wrappedReq = new ContentCachingRequestWrapper(request, MAX_BODY_LOG_SIZE);
        ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(response);

        Throwable thrown = null;
        try {
            filterChain.doFilter(wrappedReq, wrappedRes);
        } catch (ServletException | IOException ex) {
            thrown = ex;
            throw ex;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int  status  = wrappedRes.getStatus();

            String user = "anonymous";
            Principal principal = wrappedReq.getUserPrincipal();
            if (principal != null && principal.getName() != null) {
                user = principal.getName();
            }

            if (status >= 500 || thrown != null) {
                String reqBody  = readBody(wrappedReq.getContentAsByteArray());
                String respBody = readBody(wrappedRes.getContentAsByteArray());
                String errMsg   = thrown != null ? thrown.getMessage() : "";

                log.error("HTTP {} {} → {} | {}ms | user={} | ip={}\n  ↳ request : {}\n  ↳ response: {}\n  ↳ error   : {}",
                        method, target, status, elapsed, user, remote,
                        reqBody.isBlank()  ? "(no body)" : reqBody,
                        respBody.isBlank() ? "(no body)" : respBody,
                        errMsg.isBlank()   ? "(none)"    : errMsg);

            } else if (status >= 400) {
                String reqBody  = readBody(wrappedReq.getContentAsByteArray());
                String respBody = readBody(wrappedRes.getContentAsByteArray());

                log.warn("HTTP {} {} → {} | {}ms | user={} | ip={}\n  ↳ request : {}\n  ↳ response: {}",
                        method, target, status, elapsed, user, remote,
                        reqBody.isBlank()  ? "(no body)" : reqBody,
                        respBody.isBlank() ? "(no body)" : respBody);

            } else {
                log.info("HTTP {} {} → {} | {}ms | user={} | ip={}",
                        method, target, status, elapsed, user, remote);
            }

            // Must copy cached response body back to the real response stream
            wrappedRes.copyBodyToResponse();
        }
    }

    /** Skip logging for Spring Boot actuator/health pings to reduce noise. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.equals("/favicon.ico");
    }

    private String readBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String body = new String(bytes, StandardCharsets.UTF_8).strip();
        if (body.length() > MAX_BODY_LOG_SIZE) {
            body = body.substring(0, MAX_BODY_LOG_SIZE) + "… [truncated]";
        }
        return body;
    }
}
