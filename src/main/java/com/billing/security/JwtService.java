package com.billing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String AUTH_TYPE_CLAIM = "authType";
    private static final String AUTH_TYPE_USER = "USER";
    private static final String AUTH_TYPE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @PostConstruct
    public void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT secret is not configured. Set the JWT_SECRET environment variable or app.jwt.secret property.");
        }
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(AUTH_TYPE_CLAIM, AUTH_TYPE_USER);
        claims.put("role", userDetails.getRole());
        claims.put("companyId", userDetails.getCompanyId());
        claims.put("userId", userDetails.getId());

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userDetails.getId()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generatePlatformAdminAccessToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(AUTH_TYPE_CLAIM, AUTH_TYPE_PLATFORM_ADMIN);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Long tokenUserId = extractUserId(token);
        if (tokenUserId != null && userDetails instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getId().equals(tokenUserId) && !isTokenExpired(token);
        }
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        Object userId = extractClaim(token, claims -> claims.get("userId"));
        if (userId instanceof Number number) {
            return number.longValue();
        }
        if (userId instanceof String value && !value.isBlank()) {
            return Long.parseLong(value);
        }
        String subject = extractUsername(token);
        if (subject != null && subject.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(subject);
        }
        return null;
    }

    public String extractAuthType(String token) {
        Object authType = extractClaim(token, claims -> claims.get(AUTH_TYPE_CLAIM));
        return authType instanceof String value && !value.isBlank() ? value : AUTH_TYPE_USER;
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
        return resolver.apply(claims);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration / 1000;
    }

    private SecretKey getSigningKey() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT secret is not configured. Set the JWT_SECRET environment variable or app.jwt.secret property.");
        }
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        try {
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT secret configuration is invalid. Ensure JWT_SECRET is set to a sufficiently long secret.", ex);
        }
    }
}
