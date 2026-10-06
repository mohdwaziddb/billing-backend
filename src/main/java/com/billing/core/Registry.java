package com.billing.core;

import com.billing.platform.tomcat.MysqlDataSourceService;
import java.util.HashMap;
import java.util.Map;

public class Registry {

    public static boolean IS_ONLINE = true;
    public static boolean IS_AWS = false;

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
}
