package com.billing.dto.dataport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Common DataPort row: har module ke Excel columns {@code values} map me
 * (field key -> text) aate hain, isliye ek hi preview/edit/import flow
 * saare modules ke liye chalta hai.
 */
@Getter
@Setter
@NoArgsConstructor
public class DataPortRow implements ValidatableImportRow {
    private Integer rowNumber;
    private Map<String, String> values = new LinkedHashMap<>();
    private boolean valid = true;
    private Map<String, String> validationErrors = new LinkedHashMap<>();

    // Validation ke dauraan resolve hue ids (category id, product, invoice ...).
    // Client ko nahi bheje jaate; import se pehle server dobara validate karta hai.
    @JsonIgnore
    private Map<String, Object> resolved = new LinkedHashMap<>();

    public DataPortRow(Integer rowNumber, Map<String, String> values) {
        this.rowNumber = rowNumber;
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    public String value(String key) {
        String value = values.get(key);
        return value == null ? "" : value.trim();
    }

    public void error(String field, String message) {
        validationErrors.putIfAbsent(field, message);
    }

    @SuppressWarnings("unchecked")
    public <T> T resolvedValue(String key) {
        return (T) resolved.get(key);
    }
}
