package com.billing.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int WINDOW_STEPS = 1;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String buildOtpAuthUrl(String secret, String username, String companyCode, String deviceName) {
        String safeUser = safeText(username);
        if (safeUser.isEmpty()) {
            safeUser = "user";
        }
        String safeCompany = safeText(companyCode);
        if (safeCompany.isEmpty()) {
            safeCompany = "Bizio";
        }
        String label = safeCompany + ":" + safeUser;
        String issuer = safeCompany;
        if (deviceName != null && !deviceName.isBlank()) {
            issuer = safeCompany + " " + safeText(deviceName);
        }
        return "otpauth://totp/" + urlEncode(label)
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&digits=6&period=30";
    }

    public String buildQrBase64(String otpAuthUrl) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 280, 280);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build MFA QR code", ex);
        }
    }

    public boolean verifyCode(String secret, String code) {
        return matchingStep(secret, code) >= 0;
    }

    /**
     * Code jis TOTP time-step se match hua wo step (replay check ke liye), warna -1.
     */
    public long matchingStep(String secret, String code) {
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isEmpty() || !normalizedCode.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        if (normalizedCode.length() != CODE_DIGITS) {
            return -1;
        }
        byte[] key;
        try {
            key = base32Decode(secret);
        } catch (Exception ex) {
            return -1;
        }
        long step = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        for (long offset = -WINDOW_STEPS; offset <= WINDOW_STEPS; offset++) {
            String candidate = generateTotp(key, step + offset);
            if (candidate.equals(normalizedCode)) {
                return step + offset;
            }
        }
        return -1;
    }

    private String generateTotp(byte[] key, long step) {
        try {
            byte[] data = new byte[8];
            long value = step;
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (value & 0xFF);
                value >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            String text = String.valueOf(otp);
            while (text.length() < CODE_DIGITS) {
                text = "0" + text;
            }
            return text;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify MFA code", ex);
        }
    }

    private String base32Encode(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                out.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    private byte[] base32Decode(String secret) {
        String normalized = secret == null ? "" : secret.trim().replace("=", "").replace(" ", "").toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Empty MFA secret");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : normalized.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid MFA secret character");
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 64) {
            trimmed = trimmed.substring(0, 64);
        }
        return trimmed;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception ex) {
            return value;
        }
    }
}
