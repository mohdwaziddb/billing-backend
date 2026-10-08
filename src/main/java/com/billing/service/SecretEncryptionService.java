package com.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class SecretEncryptionService {

    private static final String PREFIX = "ENC:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec keySpec;

    public SecretEncryptionService(
            @Value("${app.encryption.secret:}") String encryptionSecret,
            @Value("${app.jwt.secret:}") String jwtSecret) {
        String secret = (encryptionSecret != null && !encryptionSecret.isBlank())
                ? encryptionSecret
                : jwtSecret;
        if (secret == null || secret.isBlank()
                || secret.contains("CHANGE-ME")
                || secret.contains("billing-default-secret")
                || (secret.contains("local-dev-only-dummy-secret") && isProdProfile())) {
            throw new IllegalStateException("Secret encryption is not configured. Set the APP_ENCRYPTION_SECRET environment variable.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("Secret encryption configuration is invalid. Ensure APP_ENCRYPTION_SECRET is at least 32 characters.");
        }
        if (encryptionSecret == null || encryptionSecret.isBlank()) {
            log.warn("APP_ENCRYPTION_SECRET is not set; falling back to JWT secret for decryption compatibility. Set a separate APP_ENCRYPTION_SECRET and rotate stored secrets.");
        }
        this.keySpec = new SecretKeySpec(sha256(secret), "AES");
    }

    private boolean isProdProfile() {
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        return profile != null && profile.contains("prod");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank() || plainText.startsWith(PREFIX)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt secret", ex);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank() || !value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Encrypted secret could not be decrypted; treating value as unavailable");
            return null;
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize secret encryption", ex);
        }
    }
}
