package com.billing.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SanitizeData {
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

    public static Map<String, Object> sanitizeMapObj(Map<String, Object> map) {
        Map<String, Object> sanitizeMap = new HashMap<>();
        if (map != null && map.size() > 0) {
            map.forEach((paramName, inputobj) -> {
                if (inputobj instanceof ArrayList) {
                    ArrayList<Object> resultList = new ArrayList<>();
                    ((ArrayList<?>) inputobj).forEach(obj -> {
                        String result = sanitizeData(paramName, obj);
                        resultList.add(result);
                    });
                    sanitizeMap.put(paramName, resultList);
                } else if (inputobj instanceof HashMap) {
                    HashMap<String,String> resultMap = new HashMap();
                    ((HashMap<?, ?>) inputobj).forEach((key,value)->{
                        String result = sanitizeData(DataTypeUtility.stringNullValue(key), value);
                        resultMap.put(DataTypeUtility.stringNullValue(key), result);
                    });
                    sanitizeMap.put(paramName, resultMap);
                } else {
                    String result = sanitizeData(paramName, inputobj);
                    sanitizeMap.put(paramName, result);
                }
            });
        }
        return sanitizeMap;
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
