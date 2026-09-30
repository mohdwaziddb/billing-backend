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
        Registry.dbmap.put("url", "localhost:3306");
        Registry.dbmap.put("urlreader", "localhost:3306");
        Registry.dbmap.put("username", "root");
        Registry.dbmap.put("password", "root");
        Registry.dbmap.put("driverClassName", "com.mysql.cj.jdbc.Driver");
        Registry.dbmap.put("databasename", "xyztrader");
        return Registry.dbmap;
    }
}
