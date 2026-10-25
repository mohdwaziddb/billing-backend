package com.billing.core;

import java.util.HashMap;

public class Registry {

    public static boolean IS_ONLINE = true;
    public static boolean IS_AWS = true;

    public static final HashMap<String, String> dbmap = new HashMap<>();

    /**
     * Single shared jdbcUrl -&gt; host:port extractor
     * (jdbc:mysql://localhost:3306/billing?params -&gt; localhost:3306).
     * Null/blank-safe: falls back to localhost:3306 instead of NPE.
     */
    public static String extractHostPort(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "localhost:3306";
        }
        String tmp = jdbcUrl.replace("jdbc:mysql://", "");
        int slash = tmp.indexOf('/');
        if (slash > 0) {
            tmp = tmp.substring(0, slash);
        }
        tmp = tmp.trim();
        if (tmp.isEmpty()) {
            return "localhost:3306";
        }
        return tmp;
    }
}
