package com.billing.service.invoice;

import com.billing.exception.BadRequestException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceTemplateRegistryService {

    private static final String TEMPLATE_ROOT = "classpath*:invoice-templates/*/template.json";

    private final ObjectMapper objectMapper;

    private final Map<String, InvoiceTemplateDefinition> cache = new LinkedHashMap<>();

    @PostConstruct
    public void initialize() {
        refresh();
    }

    public synchronized void refresh() {
        cache.clear();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(TEMPLATE_ROOT);
            for (Resource metadataResource : resources) {
                registerTemplate(metadataResource);
            }
        } catch (IOException ex) {
            throw new BadRequestException("Unable to load invoice templates");
        }
        if (cache.isEmpty()) {
            throw new BadRequestException("No invoice templates were found");
        }
    }

    public List<InvoiceTemplateDefinition> templates() {
        return cache.values().stream()
                .sorted(Comparator.comparing(InvoiceTemplateDefinition::getTemplateName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public InvoiceTemplateDefinition getOrThrow(String templateId) {
        InvoiceTemplateDefinition definition = cache.get(templateId);
        if (definition == null) {
            throw new BadRequestException("Invoice template not found: " + templateId);
        }
        return definition;
    }

    public String defaultTemplateId() {
        return cache.keySet().stream().findFirst().orElse("classic-corporate");
    }

    private void registerTemplate(Resource metadataResource) throws IOException {
        Map<String, Object> metadata;
        try (InputStream inputStream = metadataResource.getInputStream()) {
            metadata = objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
        }
        String url = metadataResource.getURL().toString();
        String basePath = url.substring(0, url.lastIndexOf('/'));
        Resource htmlResource = metadataResource.createRelative("template.html");
        Resource cssResource = metadataResource.createRelative("template.css");
        if (!htmlResource.exists() || !cssResource.exists()) {
            return;
        }
        String templateId = stringValue(metadata.get("templateId"));
        if (templateId == null || templateId.isBlank()) {
            return;
        }
        InvoiceTemplateDefinition definition = InvoiceTemplateDefinition.builder()
                .templateId(templateId)
                .templateName(defaultString(stringValue(metadata.get("templateName")), templateId))
                .version(defaultString(stringValue(metadata.get("version")), "1.0.0"))
                .description(defaultString(stringValue(metadata.get("description")), "Invoice template"))
                .author(defaultString(stringValue(metadata.get("author")), "BizFinity"))
                .previewImage(stringValue(metadata.get("previewImage")))
                .supportsWatermark(booleanValue(metadata.get("supportsWatermark"), true))
                .supportsQr(booleanValue(metadata.get("supportsQr"), true))
                .supportsSignature(booleanValue(metadata.get("supportsSignature"), true))
                .supportsBankDetails(booleanValue(metadata.get("supportsBankDetails"), true))
                .supportsTerms(booleanValue(metadata.get("supportsTerms"), true))
                .supportsNotes(booleanValue(metadata.get("supportsNotes"), true))
                .supportsGst(booleanValue(metadata.get("supportsGst"), true))
                .supportsMultiPage(booleanValue(metadata.get("supportsMultiPage"), true))
                .supportedPaperSizes(stringList(metadata.get("supportedPaperSizes")))
                .defaultColors(stringMap(metadata.get("defaultColors")))
                .htmlTemplate(htmlResource.getContentAsString(StandardCharsets.UTF_8))
                .cssTemplate(cssResource.getContentAsString(StandardCharsets.UTF_8))
                .folderName(basePath.substring(basePath.lastIndexOf('/') + 1))
                .build();
        cache.put(templateId, definition);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of("A4");
    }

    private Map<String, String> stringMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((key, entryValue) -> result.put(String.valueOf(key), String.valueOf(entryValue)));
            return result;
        }
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("accent", "#0f172a");
        fallback.put("muted", "#64748b");
        fallback.put("surface", "#f8fafc");
        return fallback;
    }
}
