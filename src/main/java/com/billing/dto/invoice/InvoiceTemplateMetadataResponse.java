package com.billing.dto.invoice;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class InvoiceTemplateMetadataResponse {
    private String templateId;
    private String templateName;
    private String version;
    private String description;
    private String author;
    private String previewImage;
    private boolean supportsWatermark;
    private boolean supportsQr;
    private boolean supportsSignature;
    private boolean supportsBankDetails;
    private boolean supportsTerms;
    private boolean supportsNotes;
    private boolean supportsGst;
    private boolean supportsMultiPage;
    private List<String> supportedPaperSizes;
    private Map<String, String> defaultColors;
    private boolean defaultTemplate;
}
