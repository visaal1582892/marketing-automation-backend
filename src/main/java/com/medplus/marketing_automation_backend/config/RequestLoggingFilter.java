package com.medplus.marketing_automation_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;

/**
 * Logs every inbound HTTP request and its response status + duration.
 * Runs before Spring Security so unauthenticated requests are still captured.
 */
@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

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

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int  status  = response.getStatus();

            // Resolve principal after the filter chain (JWT filter runs inside chain)
            String user = "anonymous";
            Principal principal = request.getUserPrincipal();
            if (principal != null && principal.getName() != null) {
                user = principal.getName();
            }

            if (status >= 500) {
                log.error("HTTP {} {} → {} | {}ms | user={} | ip={}",
                        method, target, status, elapsed, user, remote);
            } else if (status >= 400) {
                log.warn("HTTP {} {} → {} | {}ms | user={} | ip={}",
                        method, target, status, elapsed, user, remote);
            } else {
                log.info("HTTP {} {} → {} | {}ms | user={} | ip={}",
                        method, target, status, elapsed, user, remote);
            }
        }
    }

    /** Skip logging for Spring Boot actuator/health pings to reduce noise. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.equals("/favicon.ico");
    }
}
