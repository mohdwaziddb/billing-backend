package com.billing.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SanitizeData {

    /**
     * Max traversal depth. Deeper levels fall back to flat string cleaning
     * (the historical behavior) so pathological input can never overflow.
     */
    private static final int MAX_DEPTH = 20;

    public static Map<String, String> sanitizeMap(Map<String, String> map) {
        Map<String, String> sanitizeMap = new HashMap<>();
        if (map != null && map.size() > 0) {
            map.forEach((paramName, input) -> {
                String result = sanitizeData(paramName, input);
                sanitizeMap.put(paramName, result);
            });
        }
        return sanitizeMap;
    }

    /**
     * Deep-sanitizes a request map WITHOUT destroying its structure.
     * Maps stay Maps, Lists stay Lists (any depth); only leaf values go
     * through the string cleaner. Scalar output is byte-identical to the
     * historical behavior, so every existing mapper keeps working.
     * Historical bug fixed here: nested objects/arrays used to be flattened
     * into garbage strings, silently dropping e.g. purchase/invoice items.
     */
    public static Map<String, Object> sanitizeMapObj(Map<String, Object> map) {
        Map<String, Object> sanitizeMap = new HashMap<>();
        if (map != null && map.size() > 0) {
            map.forEach((paramName, inputobj) -> {
                sanitizeMap.put(
                        DataTypeUtility.stringNullValue(paramName),
                        sanitizeValue(paramName, inputobj, 0));
            });
        }
        return sanitizeMap;
    }

    private static Object sanitizeValue(String paramName, Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth >= MAX_DEPTH) {
            // Fail-safe: behave like the historical flattener past the cap.
            return sanitizeData(paramName, value);
        }
        if (value instanceof Map) {
            Map<String, Object> nested = new HashMap<>();
            ((Map<?, ?>) value).forEach((key, nestedValue) -> {
                String keyName = DataTypeUtility.stringNullValue(key);
                nested.put(keyName, sanitizeValue(keyName, nestedValue, depth + 1));
            });
            return nested;
        }
        if (value instanceof java.util.List) {
            ArrayList<Object> resultList = new ArrayList<>();
            for (Object element : (java.util.List<?>) value) {
                resultList.add(sanitizeValue(paramName, element, depth + 1));
            }
            return resultList;
        }
        if (value.getClass().isArray()) {
            ArrayList<Object> resultList = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                resultList.add(sanitizeValue(paramName, java.lang.reflect.Array.get(value, i), depth + 1));
            }
            return resultList;
        }
        return sanitizeData(paramName, value);
    }

    private static String sanitizeData(String paramName, Object inputobj) {
        String result = null;
        String input = DataTypeUtility.stringNullValue(inputobj);
        if (input == null) {
        } else {
            result = DataTypeUtility.stringNullValue(input);
            if (result != null && result.contains("HYPERLINK")) {
                result = "";
            } else {
                if ((paramName != null && (paramName.contains("__formula") || paramName.contains("field_values") || paramName.equals("msg"))) || (paramName != null && paramName.contains("_url"))) {
                } else if (paramName == null || !(paramName.contains("__html"))) {
                    result = Jsoup.clean(input, Safelist.none());
                } else if (paramName.contains("__html")) {
                    if (!paramName.contains("__htmlscript")) {
                        result = Jsoup.clean(input, Safelist.relaxed());
                    }
                }
                if (input.contains("./.") || input.contains("file://")
                        || input.contains("/etc/passwd") || input.contains("127.0.0.1")) {
                    result = "";
                }
                if (result != null && result.contains("&amp;")) {
                    result = result.replaceAll("&amp;", "&");
                }
                if (result != null && result.contains("%2526")) {
                    result = result.replaceAll("%2526", "&");
                }
                if (result != null && result.contains("%26")) {
                    result = result.replaceAll("%26", "&");
                }
            }
        }
        return result;
    }
}
