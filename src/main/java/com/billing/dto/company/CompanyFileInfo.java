package com.billing.dto.company;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Full file metadata for company branding images (logo / signature / future images).
 * Serialized as JSON into the existing {@code logo_url} / {@code signature_url}
 * columns (now TEXT). The plain display URL is always available via {@code url}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyFileInfo {

    /** Stored file name, e.g. {@code company-1-a3f2c1.png}. */
    @JsonProperty("file_name")
    private String fileName;

    /** Original client-side file name, e.g. {@code my-logo.png}. */
    @JsonProperty("original_name")
    private String originalName;

    /** S3 object key, e.g. {@code maacreation/company-logos/company-1-a3f2c1.png}. Null for legacy local rows. */
    @JsonProperty("key")
    private String key;

    /** Public display URL (S3 https URL or legacy {@code /uploads/...} path). */
    @JsonProperty("url")
    private String url;

    /** MIME type, e.g. {@code image/png}. */
    @JsonProperty("type")
    private String type;

    /** File size in bytes. */
    @JsonProperty("size")
    private Long size;

    /** S3 bucket. Null when stored on local disk (S3 disabled). */
    @JsonProperty("bucket")
    private String bucket;

    /** Tenant prefix (database_name, lower-case), e.g. {@code maacreation}. */
    @JsonProperty("tenant")
    private String tenant;

    /** Storage folder/kind, e.g. {@code company-logos}. */
    @JsonProperty("kind")
    private String kind;

    /** ISO-8601 upload timestamp. */
    @JsonProperty("uploaded_at")
    private String uploadedAt;
}
