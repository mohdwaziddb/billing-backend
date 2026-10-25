package com.billing.platform.tomcat;

import com.billing.core.Registry;

import java.util.HashMap;

/**
 * Startup par Registry.dbmap bharta hai: DB host/user/password (Spring config ya env
 * DB_HOST/DB_USERNAME/DB_PASSWORD) aur default tenant `test` (jab request se company
 * pata na chale). Purana per-request DriverManager connection code hata diya:
 * ab connections Hibernate ke tenant pools dete hain.
 */
public final class MysqlDataSourceService {

    private MysqlDataSourceService() {
    }

    public static final HashMap<String, String> loadDatabaseCredentialsFromLocalHost() {
        // Single source: Spring spring.datasource.* (MultiTenancyJpaConfiguration
        // se Registry.dbmap me pehle se set) + prod env override.
        // Java me koi password literal nahi — hrhk sirf application-local.properties me.
        // billing_common + tenant same server par hai, isliye url/urlreader same.
        String envHost = System.getenv("DB_HOST");
        String envPort = System.getenv("DB_PORT");
        if (envHost != null && !envHost.isBlank()) {
            String port = (envPort == null || envPort.isBlank()) ? "3306" : envPort.trim();
            String hostPort = envHost.trim().replace("jdbc:mysql://", "").replace("/", "") + ":" + port;
            Registry.dbmap.put("url", hostPort);
            Registry.dbmap.put("urlreader", hostPort);
        } else {
            Registry.dbmap.putIfAbsent("url", "localhost:3306");
            Registry.dbmap.putIfAbsent("urlreader", "localhost:3306");
        }
        String envUser = System.getenv("DB_USERNAME");
        if (envUser != null && !envUser.isBlank()) {
            Registry.dbmap.put("username", envUser);
        } else {
            Registry.dbmap.putIfAbsent("username", "root");
        }
        String envPass = System.getenv("DB_PASSWORD");
        if (envPass != null && !envPass.isBlank()) {
            Registry.dbmap.put("password", envPass);
        }
        // else: password wahi rahega jo Spring (spring.datasource.password) se
        // MultiTenancyJpaConfiguration ne pehle set kiya — koi hardcoded fallback nahi.
        Registry.dbmap.putIfAbsent("driverClassName", "com.mysql.cj.jdbc.Driver");
        Registry.dbmap.putIfAbsent("databasename", "test");
        return Registry.dbmap;
    }
}
