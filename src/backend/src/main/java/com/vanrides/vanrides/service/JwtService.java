package com.vanrides.vanrides.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtService.class);
    @Value("${vanrides.auth.jwt-secret:}")
    private String configuredSecret;

    @Value("${vanrides.auth.access-token-ttl-minutes:15}")
    private long accessTokenTtlMinutes;

    private SecretKey signingKey;
    private JwtParser jwtParser;
    private Duration accessTokenTtl;

    @PostConstruct
    public void init() {
        byte[] secretBytes;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            secretBytes = new byte[32];
            new SecureRandom().nextBytes(secretBytes);
            log.warn("VANRIDES_JWT_SECRET ausente. Usando chave efemera apenas para desenvolvimento.");
        } else {
            secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }

        if (secretBytes.length < 32) {
            secretBytes = sha256(secretBytes);
        }

        signingKey = Keys.hmacShaKeyFor(secretBytes);
        jwtParser = Jwts.parser().verifyWith(signingKey).build();
        accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public String generateAccessToken(Long userId, String role, String email, Long companyId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey);

        if (companyId != null) {
            builder.claim("companyId", companyId);
        }

        return builder.compact();
    }

    public Claims parseAndValidate(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.getSeconds();
    }

    private byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
