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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String AUTH_TYPE_CLAIM = "authType";
    /** user_sessions.session_key: kis device ka token hai. */
    private static final String SESSION_CLAIM = "sid";
    private static final String AUTH_TYPE_USER = "USER";
    private static final String AUTH_TYPE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String AUTH_TYPE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String AUTH_TYPE_MFA_PENDING = "MFA_PENDING";
    private static final long MFA_TOKEN_EXPIRATION_MILLIS = 5 * 60 * 1000L;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    // Super-admin / platform-admin ke paas refresh token nahi hai, isliye inka
    // access token alag (lamba) rakha hai; user tokens chhote + refresh se chalte hain.
    @Value("${app.jwt.admin-access-token-expiration:28800000}")
    private long adminAccessTokenExpiration;

    @PostConstruct
    public void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()
                || jwtSecret.contains("CHANGE-ME")
                || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT secret is not configured. Set the JWT_SECRET environment variable to a 32+ character secret.");
        }
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        return generateAccessToken(userDetails, null);
    }

    /** sessionKey = user_sessions.session_key ("sid"): device hatate hi ye token band. */
    public String generateAccessToken(CustomUserDetails userDetails, String sessionKey) {
        Map<String, Object> claims = new HashMap<>();
        if (sessionKey != null) {
            claims.put(SESSION_CLAIM, sessionKey);
        }
        claims.put(AUTH_TYPE_CLAIM, AUTH_TYPE_USER);
        claims.put("role", userDetails.getRole());
        claims.put("companyCode", userDetails.getCompanyCode());
        claims.put("databaseName", userDetails.getDatabaseName());
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
                .expiration(new Date(System.currentTimeMillis() + adminAccessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateSuperAdminAccessToken(String username, String companyCode, String databaseName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(AUTH_TYPE_CLAIM, AUTH_TYPE_SUPER_ADMIN);
        claims.put("role", "OWNER");
        claims.put("companyCode", companyCode);
        claims.put("databaseName", databaseName);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + adminAccessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateMfaPendingToken(Long userId, String companyCode, String databaseName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(AUTH_TYPE_CLAIM, AUTH_TYPE_MFA_PENDING);
        claims.put("companyCode", companyCode);
        claims.put("databaseName", databaseName);
        claims.put("userId", userId);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + MFA_TOKEN_EXPIRATION_MILLIS))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isMfaPendingToken(String token) {
        try {
            return AUTH_TYPE_MFA_PENDING.equals(extractAuthType(token));
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isMfaPendingTokenValid(String token, Long expectedUserId) {
        try {
            if (!isMfaPendingToken(token)) {
                return false;
            }
            Long tokenUserId = extractUserId(token);
            if (tokenUserId == null || !tokenUserId.equals(expectedUserId)) {
                return false;
            }
            return !isTokenExpired(token);
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Long tokenUserId = extractUserId(token);
        if (tokenUserId != null && userDetails instanceof CustomUserDetails customUserDetails) {
            // isEnabled = user.active: deactivate hote hi har request rukti hai.
            return customUserDetails.getId().equals(tokenUserId) && !isTokenExpired(token)
                    && customUserDetails.isEnabled()
                    && !issuedBefore(token, customUserDetails.getTokensValidAfter());
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

    public String extractCompanyCode(String token) {
        Object code = extractClaim(token, claims -> claims.get("companyCode"));
        return code instanceof String value && !value.isBlank() ? value : null;
    }

    public String extractSessionKey(String token) {
        Object sid = extractClaim(token, claims -> claims.get(SESSION_CLAIM));
        return sid instanceof String value && !value.isBlank() ? value : null;
    }

    public String extractDatabaseName(String token) {
        Object db = extractClaim(token, claims -> claims.get("databaseName"));
        return db instanceof String value && !value.isBlank() ? value : null;
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

    public long getAdminAccessTokenExpiration() {
        return adminAccessTokenExpiration / 1000;
    }

    // JWT iat seconds me hota hai: cutoff ko bhi seconds tak truncate karke compare.
    private boolean issuedBefore(String token, LocalDateTime cutoff) {
        if (cutoff == null) {
            return false;
        }
        Date issuedAt = extractClaim(token, Claims::getIssuedAt);
        if (issuedAt == null) {
            return true;
        }
        long cutoffSeconds = cutoff.atZone(ZoneId.systemDefault()).toEpochSecond();
        return issuedAt.getTime() / 1000 < cutoffSeconds;
    }

    private SecretKey getSigningKey() {
        if (jwtSecret == null || jwtSecret.isBlank()
                || jwtSecret.contains("CHANGE-ME")
                || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT secret configuration is invalid. Ensure JWT_SECRET is set to a 32+ character secret.");
        }
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        try {
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT secret configuration is invalid. Ensure JWT_SECRET is set to a sufficiently long secret.", ex);
        }
    }
}
