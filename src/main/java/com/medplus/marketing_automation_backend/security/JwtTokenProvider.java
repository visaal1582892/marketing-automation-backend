package com.medplus.marketing_automation_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${app.jwt.issuer}")
    private String issuer;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a signed JWT.
     *
     * @param roleNames  full list of role names the user holds
     */
    public String generateToken(Long userId, String email, List<String> roleNames,
                                String departmentName, String departmentId, String fullName) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);

        List<String> roles = roleNames == null ? Collections.emptyList() : roleNames;
        String primaryRole = roles.isEmpty() ? "" : roles.get(0);

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid",          userId);
        claims.put("name",         fullName);
        claims.put("role",         primaryRole);   // primary role name — kept for backward compat
        claims.put("roles",        roles);          // full list for multi-role checks
        claims.put("department",   departmentName);
        claims.put("departmentId", departmentId);

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmail(String token) {
        return parse(token).getSubject();
    }
}
