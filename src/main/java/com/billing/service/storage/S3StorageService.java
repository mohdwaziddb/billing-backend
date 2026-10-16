package com.billing.service.storage;

import com.billing.dto.company.CompanyFileInfo;
import com.billing.core.Registry;
import com.billing.entity.Company;
import com.billing.exception.BadRequestException;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.util.DataTypeUtility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generic tenant-scoped file storage for company branding images (logo, signature)
 * and any future images. FLAT layout — sirf database folder, usme sidha files:
 * <pre>
 * s3://&lt;bucket&gt;/&lt;database_name_lower&gt;/&lt;slot&gt;-&lt;original_name&gt;
 * </pre>
 * Same naam dobara upload ho to same key banti hai, S3 in-place overwrite kar
 * deta hai (nayi object nahi banti). Alag naam aaye to purani key DB JSON se
 * delete hoti hai. Slot-prefix (logo-/signature-) taaki dono slot ka same naam
 * takraye to bhi ek dusre ko overwrite na kare.
 *
 * A single shared bucket + global IAM credentials (env vars) is used; tenants are
 * isolated by the {@code database_name} prefix. When {@code Registry.IS_AWS} is
 * false, files fall back to the local {@code uploads} directory (same flat
 * layout) so local development works without AWS.
 */
@Service
public class S3StorageService {

    private static final long ABSOLUTE_MAX_BYTES = 10L * 1024L * 1024L;

    @Value("${app.s3.bucket:}")
    private String s3Bucket;

    @Value("${app.s3.region:ap-south-1}")
    private String s3Region;

    @Value("${app.s3.public-base-url:}")
    private String s3PublicBaseUrl;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.max-bytes:2097152}")
    private long maxBytes;

    private volatile S3Client s3Client;

    /**
     * Single AWS switch for file storage: project ka existing
     * {@code Registry.IS_AWS} flag hi use hota hai (wahi datasource
     * selection me bhi use hota hai). Alag flag nahi hai.
     */
    public boolean isAws() {
        return Registry.IS_AWS;
    }

    /** True only when S3 is requested AND bucket + region are configured. */
    public boolean isEnabled() {
        return isAws()
                && DataTypeUtility.stringValue(s3Bucket).length() > 0
                && DataTypeUtility.stringValue(s3Region).length() > 0;
    }

    public long getMaxBytes() {
        return maxBytes > 0 ? Math.min(maxBytes, ABSOLUTE_MAX_BYTES) : ABSOLUTE_MAX_BYTES;
    }

    /**
     * Store an image and return its full metadata (caller serializes it to DB JSON).
     * Key = {@code <tenant>/<slot>-<original_name>} — same naam dobara aaye to
     * same key (S3/local dono overwrite), alag naam aaye to caller purani key
     * (DB JSON me stored) delete karta hai.
     *
     * @param company tenant fallback when no request-scoped tenant is bound
     * @param slot    file slot/prefix, e.g. {@code logo} or {@code signature}
     */
    public CompanyFileInfo store(Company company, String slot, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image file is required");
        }
        String contentType = DataTypeUtility.stringValue(file.getContentType()).toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new BadRequestException("Unable to read image file");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("Image file is required");
        }
        if (bytes.length > getMaxBytes()) {
            throw new BadRequestException("Image must be smaller than " + (getMaxBytes() / 1024) + " KB");
        }
        String safeSlot = sanitizeSlot(slot);
        String tenant = resolveTenant(company);
        String fileName = buildFileName(safeSlot, file.getOriginalFilename(), contentType);
        String originalName = DataTypeUtility.stringNullValue(file.getOriginalFilename());
        String uploadedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String bucket = DataTypeUtility.stringValue(s3Bucket);

        // Explicit isAws check: S3 requested -> S3 only; otherwise local only.
        // Misconfigured S3 (flag ON but bucket/region missing) fails fast instead
        // of silently storing locally, so prod mistakes surface immediately.
        if (isAws() && !isEnabled()) {
            throw new BadRequestException("S3 image storage is enabled but bucket/region is not configured");
        }
        // Flat key: sirf database folder, usme sidha file (koi kind subfolder nahi).
        String key = tenant + "/" + fileName;
        if (isEnabled()) {
            try {
                PutObjectRequest put = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) bytes.length)
                        .cacheControl("public, max-age=31536000")
                        .build();
                client().putObject(put, RequestBody.fromBytes(bytes));
            } catch (BadRequestException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BadRequestException("Unable to upload company image");
            }
            return CompanyFileInfo.builder()
                    .fileName(fileName)
                    .originalName(originalName)
                    .key(key)
                    .url(publicUrl(key))
                    .type(contentType)
                    .size((long) bytes.length)
                    .bucket(bucket)
                    .tenant(tenant)
                    .kind(safeSlot)
                    .uploadedAt(uploadedAt)
                    .build();
        }

        // Local fallback (S3 disabled): same flat layout under uploads/.
        // Same key dobara aaye to TRUNCATE overwrite kar deta hai.
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetFile = baseDir.resolve(key).normalize();
        if (!targetFile.startsWith(baseDir)) {
            throw new BadRequestException("Invalid image path");
        }
        try {
            Files.createDirectories(targetFile.getParent());
            Files.write(targetFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ex) {
            throw new BadRequestException("Unable to upload company image");
        }
        return CompanyFileInfo.builder()
                .fileName(fileName)
                .originalName(originalName)
                .key(key)
                .url("/uploads/" + key)
                .type(contentType)
                .size((long) bytes.length)
                .tenant(tenant)
                .kind(safeSlot)
                .uploadedAt(uploadedAt)
                .build();
    }

    /** Best-effort S3 delete. Never throws (callers must not fail on cleanup). */
    public void deleteByKey(String key) {
        String cleanKey = DataTypeUtility.stringValue(key);
        if (cleanKey.length() == 0 || !isEnabled()) {
            return;
        }
        try {
            client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(DataTypeUtility.stringValue(s3Bucket))
                    .key(cleanKey)
                    .build());
        } catch (Exception ignored) {
            // Cleanup must never break the main flow.
        }
    }

    /**
     * Best-effort local-file delete by relative path (e.g. {@code test/logo-x.png}).
     * S3-disabled fallback ke liye. Never throws.
     */
    public void deleteLocalPath(String relativePath) {
        String clean = DataTypeUtility.stringValue(relativePath).replace("\\", "/");
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.length() == 0 || clean.contains("..")) {
            return;
        }
        try {
            Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetFile = baseDir.resolve(clean).normalize();
            if (!targetFile.startsWith(baseDir)) {
                return;
            }
            Files.deleteIfExists(targetFile);
        } catch (Exception ignored) {
            // Cleanup must never break the main flow.
        }
    }

    /** Tenant folder: request tenant (database_name) wins, company is the fallback. */
    public String resolveTenant(Company company) {
        String tenant = DataTypeUtility.stringValue(TenantContextHolder.getTenant())
                .replace("_read", "").replace("_write", "");
        if (tenant.length() > 0 && !"billing_common".equalsIgnoreCase(tenant)) {
            return sanitizeTenant(tenant);
        }
        if (company != null) {
            String databaseName = DataTypeUtility.stringValue(company.getDatabaseName());
            if (databaseName.length() > 0) {
                return sanitizeTenant(databaseName);
            }
            String code = DataTypeUtility.stringValue(company.getCode());
            if (code.length() > 0) {
                return sanitizeTenant(code);
            }
            Long companyId = company.getId();
            if (companyId != null) {
                return "company-" + companyId;
            }
        }
        return "common";
    }

    private String publicUrl(String key) {
        String base = DataTypeUtility.stringValue(s3PublicBaseUrl);
        if (base.length() > 0) {
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + "/" + key;
        }
        return "https://" + DataTypeUtility.stringValue(s3Bucket) + ".s3." + DataTypeUtility.stringValue(s3Region) + ".amazonaws.com/" + key;
    }

    private S3Client client() {
        S3Client result = s3Client;
        if (result == null) {
            synchronized (this) {
                result = s3Client;
                if (result == null) {
                    // Default credentials chain: AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY env vars.
                    result = S3Client.builder().region(Region.of(DataTypeUtility.stringValue(s3Region))).build();
                    s3Client = result;
                }
            }
        }
        return result;
    }

    private String sanitizeTenant(String tenant) {
        String clean = DataTypeUtility.stringValue(tenant).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-{2,}", "-");
        clean = clean.replaceAll("^-+|-+$", "");
        return clean.isEmpty() ? "common" : clean;
    }

    private String sanitizeSlot(String slot) {
        String clean = DataTypeUtility.stringValue(slot).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-{2,}", "-");
        clean = clean.replaceAll("^-+|-+$", "");
        if (clean.isEmpty() || clean.length() > 32) {
            throw new BadRequestException("Invalid image slot");
        }
        return clean;
    }

    /**
     * Deterministic file name: {@code <slot>-<original_name>}. Same file dobara
     * aaye to same naam (S3/local overwrite). Extension content-type se verify:
     * missing/bogus ho to sahi wali lagti hai.
     */
    private String buildFileName(String safeSlot, String originalName, String contentType) {
        String base = DataTypeUtility.stringValue(originalName).replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-").replaceAll("-{2,}", "-");
        base = base.replaceAll("^[-.]+|[-.]+$", "");
        String namePart = base;
        String ext = "";
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            namePart = base.substring(0, dot);
            ext = base.substring(dot);
        }
        String validExt = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        if (".jpeg".equals(ext)) {
            ext = ".jpg";
        } else if (!".png".equals(ext) && !".jpg".equals(ext) && !".webp".equals(ext)) {
            ext = validExt;
        }
        if (namePart.isEmpty()) {
            namePart = "file";
        }
        if (namePart.length() > 100) {
            namePart = namePart.substring(0, 100);
        }
        return safeSlot + "-" + namePart + ext;
    }
}
