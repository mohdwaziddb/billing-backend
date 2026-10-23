package com.billing.service.storage;

import com.billing.dto.company.CompanyFileInfo;
import com.billing.entity.Company;
import com.billing.exception.BadRequestException;
import com.billing.util.CompanyFileJson;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * Har image column (company logo / signature, user photo, aage koi bhi) ka
 * common flow: type check -> tenant folder me actual naam se store -> full
 * file JSON (DB column me save karne ke liye) -> save ke BAAD purani file cleanup.
 *
 * <pre>
 * String next = tenantImageService.store(company, "user-photo", file, user.getPhotoUrl(), "Photo");
 * String old = user.getPhotoUrl();
 * user.setPhotoUrl(next);
 * repository.save(user);
 * tenantImageService.cleanupReplaced(old, next);
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class TenantImageService {

    public static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final S3StorageService s3StorageService;

    /**
     * @param currentDbValue column ki current value — isi owner ki purani key same naam par overwrite ho sake
     * @param label          error messages ke liye, e.g. "Logo", "Photo"
     * @return DB column me save karne layak full file JSON
     */
    public String store(Company company, String kind, MultipartFile file, String currentDbValue, String label) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(label + " file is required");
        }
        String contentType = DataTypeUtility.stringValue(file.getContentType()).toLowerCase(Locale.ROOT);
        if (!IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Only JPG, PNG, and WEBP images are allowed");
        }
        CompanyFileInfo meta = s3StorageService.store(company, kind, file, CompanyFileJson.extractKey(currentDbValue));
        return CompanyFileJson.toJson(meta);
    }

    /**
     * Naya JSON save hone ke BAAD: key badli ho to purani file hatao. Same key
     * (same naam dobara) = in-place overwrite, delete skip warna abhi wali ud jati.
     */
    public void cleanupReplaced(String oldDbValue, String newDbValue) {
        String oldKey = DataTypeUtility.stringValue(CompanyFileJson.extractKey(oldDbValue));
        String newKey = DataTypeUtility.stringValue(CompanyFileJson.extractKey(newDbValue));
        if (oldKey.length() > 0 && oldKey.equals(newKey)) {
            return;
        }
        delete(oldDbValue);
    }

    /**
     * Best-effort cleanup of a stored image. New JSON rows: S3 key delete (S3 on)
     * ya local relative-path delete (S3 off). Legacy plain {@code /uploads/...}
     * rows: URL se derived local path delete. Never throws.
     */
    public void delete(String dbValue) {
        if (DataTypeUtility.stringValue(dbValue).length() == 0) {
            return;
        }
        try {
            String key = CompanyFileJson.extractKey(dbValue);
            if (key != null) {
                if (s3StorageService.isEnabled()) {
                    s3StorageService.deleteByKey(key);
                    return;
                }
                s3StorageService.deleteLocalPath(key);
                return;
            }
            String relativePath = CompanyFileJson.extractRelativePath(dbValue);
            if (relativePath != null) {
                s3StorageService.deleteLocalPath(relativePath);
            }
        } catch (Exception ignored) {
            // Cleanup must never break the main flow.
        }
    }
}
