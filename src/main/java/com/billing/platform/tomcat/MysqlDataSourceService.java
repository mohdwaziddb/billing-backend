package com.billing.platform.tomcat;

import com.billing.core.Registry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;

public class MysqlDataSourceService extends AbstractDataSourceService {

    @Override
    public Connection getNewConnection(HttpServletRequest request) throws SQLException {
        String database = (String) request.getAttribute("REQUEST_DATABASE_NAME");
        database = setSession(request, database);
        String username = (String) request.getAttribute("___user___");
        String password = (String) request.getAttribute("___pwd___");
        String databaseip = (String) request.getAttribute("___databaseip___");
        if (username == null) {
            username = Registry.dbmap.get("username");
        }
        if (password == null) {
            password = Registry.dbmap.get("password");
        }
        if (databaseip == null) {
            databaseip = Registry.dbmap.get("url");
        }
        if (databaseip == null) {
            databaseip = "localhost:3306";
        }
        databaseip = databaseip.replace("jdbc:mysql://", "").replace("/", "");
        String url = "jdbc:mysql://" + databaseip + "/" + database;
        return getConnection(username, password, url);
    }

    private String setSession(HttpSession session, String database) {
        if (database == null || database.equalsIgnoreCase("null")) {
            database = Registry.dbmap.get("databasename");
            if (database == null || database.isBlank()) {
                throw new RuntimeException("Unable to resolve company database for this request. Please contact administrator.");
            }
            session.setAttribute("DATABASE_NAME", database);
            session.setAttribute("VERSION_NAME", "v2");
            session.setAttribute("database", database);
            session.setAttribute("user", Registry.dbmap.get("username"));
            session.setAttribute("pwd", Registry.dbmap.get("password"));
            session.setAttribute("databaseip", Registry.dbmap.get("url"));
            session.setAttribute("version", "v2");
        }
        return database;
    }

    private String setSession(HttpServletRequest request, String database) {
        if (database == null || database.equalsIgnoreCase("null")) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                database = (String) session.getAttribute("DATABASE_NAME");
            }
            if (database == null || database.equalsIgnoreCase("null")) {
                session = request.getSession();
                database = Registry.dbmap.get("databasename");
                if (database == null || database.isBlank()) {
                    throw new RuntimeException("Unable to resolve company database for this request. Please contact administrator.");
                }
                session.setAttribute("DATABASE_NAME", database);
                session.setAttribute("VERSION_NAME", "v2");
                session.setAttribute("database", database);
                session.setAttribute("user", Registry.dbmap.get("username"));
                session.setAttribute("pwd", Registry.dbmap.get("password"));
                session.setAttribute("databaseip", Registry.dbmap.get("url"));
                session.setAttribute("version", "v2");
                request.setAttribute("___user___", Registry.dbmap.get("username"));
                request.setAttribute("___pwd___", Registry.dbmap.get("password"));
                request.setAttribute("___databaseip___", Registry.dbmap.get("url"));
            }
        }
        return database;
    }

    private Connection getConnection(String username, String password, String url) throws SQLException {
        try {
            if (url.contains("nodatabase")) {
                throw new RuntimeException("Unable to connect Mysql:Unknown database 'nodatabase'");
            }
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url + "?useUnicode=true&useEncoding=true&characterEncoding=utf-8&serverTimezone=Asia/Kolkata", username, password);
            conn.setAutoCommit(false);
            return conn;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Problem while loading Mysql Driver" + e.getMessage(), e);
        }
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
