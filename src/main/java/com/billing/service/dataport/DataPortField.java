package com.billing.service.dataport;

import java.util.List;

/**
 * Ek Excel column ki definition. Sample file, instructions sheet, preview
 * table (input type / dropdown) — sab isi se bante hain.
 */
public record DataPortField(String key,
                            String header,
                            boolean required,
                            Type type,
                            List<String> options,
                            String hint) {

    public enum Type { TEXT, NUMBER, DECIMAL, DATE, BOOLEAN, CHOICE }

    public static DataPortField required(String key, String header, Type type, String hint) {
        return new DataPortField(key, header, true, type, List.of(), hint);
    }

    public static DataPortField optional(String key, String header, Type type, String hint) {
        return new DataPortField(key, header, false, type, List.of(), hint);
    }

    public static DataPortField choice(String key, String header, boolean required, List<String> options, String hint) {
        return new DataPortField(key, header, required, Type.CHOICE, options, hint);
    }

    /** Excel header: required column par "*" (sample ke saath match hota hai). */
    public String excelHeader() {
        return required ? header + "*" : header;
    }
}
