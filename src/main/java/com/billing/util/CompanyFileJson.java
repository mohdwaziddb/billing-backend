package com.billing.util;

import com.billing.dto.company.CompanyFileInfo;
import com.billing.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;
import java.util.Locale;

/**
 * Lenient JSON helper for the {@code companies.logo_url} / {@code companies.signature_url}
 * columns, which now store full file JSON but may still hold legacy plain values
 * ({@code /uploads/...} paths or absolute URLs) from before the S3 migration.
 *
 * <p>Every reader MUST go through {@link #extractUrl(String)} / {@link #parse(String)}
 * instead of using the raw column value, so old rows keep working.
 */
public final class CompanyFileJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CompanyFileJson() {
    }

    /** Serialize file metadata for DB storage. Never returns blank. */
    public static String toJson(CompanyFileInfo info) {
        if (info == null || DataTypeUtility.stringValue(info.getUrl()).length() == 0) {
            throw new BadRequestException("Unable to save company image");
        }
        try {
            return MAPPER.writeValueAsString(info);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to save company image");
        }
    }

    /**
     * Parse a DB column value into metadata. Returns {@code null} for blank values.
     * Legacy plain URLs/paths are wrapped into a minimal {@link CompanyFileInfo}
     * (url + file_name only) so old rows behave like new ones.
     */
    public static CompanyFileInfo parse(String dbValue) {
        String trimmed = DataTypeUtility.stringValue(dbValue);
        if (trimmed.length() == 0) {
            return null;
        }
        if (trimmed.startsWith("{")) {
            try {
                CompanyFileInfo info = MAPPER.readValue(trimmed, CompanyFileInfo.class);
                if (info != null && info.getUrl() != null && !info.getUrl().isBlank()) {
                    return info;
                }
            } catch (Exception ignored) {
                // Fall through to legacy handling below.
            }
            return null;
        }
        // Legacy plain value: absolute URL or /uploads/... path.
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || trimmed.startsWith("/")) {
            String fileName = null;
            try {
                var fileNamePath = Paths.get(trimmed.contains("://") ? new java.net.URI(trimmed).getPath() : trimmed).getFileName();
                fileName = fileNamePath == null ? null : fileNamePath.toString();
            } catch (Exception ignored) {
                fileName = null;
            }
            return CompanyFileInfo.builder()
                    .fileName(fileName)
                    .url(trimmed)
                    .build();
        }
        return null;
    }

    /** Display URL for a DB column value. {@code null} when there is no image. */
    public static String extractUrl(String dbValue) {
        CompanyFileInfo info = parse(dbValue);
        return info == null ? null : info.getUrl();
    }

    /** S3 object key for a DB column value, or {@code null} for legacy/local rows. */
    public static String extractKey(String dbValue) {
        CompanyFileInfo info = parse(dbValue);
        if (info == null || DataTypeUtility.stringValue(info.getKey()).length() == 0) {
            return null;
        }
        return info.getKey().trim();
    }

    /** Stored file name for a DB column value, or {@code null} when unknown. */
    public static String extractFileName(String dbValue) {
        CompanyFileInfo info = parse(dbValue);
        if (info == null) {
            return null;
        }
        if (DataTypeUtility.stringValue(info.getFileName()).length() > 0) {
            return info.getFileName().trim();
        }
        try {
            var fileNamePath = Paths.get(info.getUrl()).getFileName();
            return fileNamePath == null ? null : fileNamePath.toString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
