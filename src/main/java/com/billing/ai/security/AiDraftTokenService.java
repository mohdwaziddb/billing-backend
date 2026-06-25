package com.billing.ai.security;

import com.billing.ai.context.AiUserContext;
import com.billing.ai.dto.AiDraftTokenPayload;
import com.billing.ai.parser.AiOperation;
import com.billing.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class AiDraftTokenService {

    private static final String TOKEN_TYPE = "AI_DRAFT";

    private final ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${ai.chat.draft-expiration-minutes:15}")
    private long draftExpirationMinutes;

    public AiDraftTokenService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DraftToken create(AiUserContext context, AiOperation operation, Map<String, Object> payload) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(draftExpirationMinutes);
        AiDraftTokenPayload tokenPayload = AiDraftTokenPayload.builder()
                .companyId(context.getCompanyId())
                .userId(context.getUserId())
                .operation(operation)
                .payload(payload)
                .build();
        try {
            String token = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .claim("type", TOKEN_TYPE)
                    .claim("payload", objectMapper.writeValueAsString(tokenPayload))
                    .issuedAt(new Date())
                    .expiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
                    .signWith(signingKey())
                    .compact();
            return new DraftToken(token, expiresAt);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to create AI draft");
        }
    }

    public AiDraftTokenPayload parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TOKEN_TYPE.equals(claims.get("type", String.class))) {
                throw new BadRequestException("Invalid AI draft");
            }
            return objectMapper.readValue(claims.get("payload", String.class), AiDraftTokenPayload.class);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (RuntimeException | JsonProcessingException ex) {
            throw new BadRequestException("AI draft has expired or is invalid");
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public record DraftToken(String token, LocalDateTime expiresAt) {
    }
}
