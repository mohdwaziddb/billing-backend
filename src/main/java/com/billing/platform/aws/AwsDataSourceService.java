package com.billing.platform.aws;

import com.billing.core.AppDomains;
import com.billing.core.Registry;
import com.billing.platform.tomcat.AbstractDataSourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Properties;
import java.io.InputStream;

public class AwsDataSourceService extends AbstractDataSourceService {

    @Override
    public Connection getNewConnection(HttpServletRequest request) throws SQLException {
        String database = (String) request.getAttribute("REQUEST_DATABASE_NAME");
        database = setDBVariable(request, database);
        String username = Registry.dbmap.get("username");
        String password = Registry.dbmap.get("password");
        String dbUrl = Registry.dbmap.get("url") + database;
        return getConnection(username, password, dbUrl);
    }

    @Override
    public Connection getNewConnection(HttpServletRequest request, String database) throws SQLException {
        if (database == null || database.length() == 0) {
            database = (String) request.getAttribute("REQUEST_DATABASE_NAME");
            database = setDBVariable(request, database);
        }
        String username = Registry.dbmap.get("username");
        String password = Registry.dbmap.get("password");
        String dbUrl = Registry.dbmap.get("url") + database;
        return getConnection(username, password, dbUrl);
    }

    private String setDBVariable(HttpServletRequest request, String database) {
        if (database == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                database = (String) session.getAttribute("DATABASE_NAME");
            }
            if (database == null) {
                String url = request.getRequestURL().toString();
                String domain = url.split("/").length > 2 ? url.split("/")[2] : AppDomains.LOCAL_BACKEND_HOST;
                database = Registry.dbmap.get("databasename");
                if (database == null) {
                    database = "billing_common";
                }
                request.setAttribute("REQUEST_DATABASE_NAME", database);
                request.setAttribute("DATABASE_NAME", database);
            }
        }
        return database;
    }

    public Connection getConnection(String username, String password, String url) throws SQLException {
        try {
            if (url.contains("nodatabase")) {
                throw new RuntimeException("Unable to connect Mysql:Unknown database 'nodatabase[" + url + "]'");
            }
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Kolkata", username, password);
            conn.setAutoCommit(false);
            return conn;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Problem while loading Mysql Driver" + e.getMessage(), e);
        }
    }

    public static final HashMap<String, String> loadDatabaseCredentialsFromLocalHost() {
        return com.billing.platform.tomcat.MysqlDataSourceService.loadDatabaseCredentialsFromLocalHost();
    }

    public static final HashMap<String, String> loadDatabaseCredentialsFromEnv() {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (url != null && !url.isBlank()) {
            Registry.dbmap.put("url", extractHostPort(url));
            Registry.dbmap.put("urlreader", extractHostPort(url));
        }
        if (username != null && !username.isBlank()) {
            Registry.dbmap.put("username", username);
        }
        if (password != null && !password.isBlank()) {
            Registry.dbmap.put("password", password);
        }
        Registry.dbmap.putIfAbsent("driverClassName", "com.mysql.cj.jdbc.Driver");
        Registry.dbmap.putIfAbsent("databasename", "billing_common");
        return Registry.dbmap;
    }

    private static String extractHostPort(String jdbcUrl) {
        try {
            String tmp = jdbcUrl.replace("jdbc:mysql://", "");
            int slash = tmp.indexOf("/");
            if (slash > 0) {
                tmp = tmp.substring(0, slash);
            }
            return tmp;
        } catch (Exception e) {
            return jdbcUrl;
        }
    }
}
