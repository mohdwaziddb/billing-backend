package com.billing.core;

import com.billing.platform.tomcat.MysqlDataSourceService;
import java.util.HashMap;
import java.util.Map;

public class Registry {

    public static boolean IS_ONLINE = true;
    public static boolean IS_AWS = true;

    private static final Map<String, Object> registry = new HashMap<String, Object>();
    public static final HashMap<String, String> dbmap = new HashMap<>();

    static {
        if (IS_AWS) {
            IS_ONLINE = true;
            try {
                registry.put("DATA_SOURCE", Class.forName("com.billing.platform.aws.AwsDataSourceService").newInstance());
                registry.put("FILE_UTILITY", new com.billing.platform.tomcat.MysqlDataSourceService());
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            registry.put("DATA_SOURCE", new MysqlDataSourceService());
            registry.put("FILE_UTILITY", new MysqlDataSourceService());
        }
    }

    @SuppressWarnings("unchecked")
    public static <X> X get(String property) {
        return (X) registry.get(property);
    }

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
