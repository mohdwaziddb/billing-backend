package com.billing.platform.tomcat;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.SQLException;

public abstract class AbstractDataSourceService {

    public abstract Connection getNewConnection(HttpServletRequest request) throws SQLException;

    public Connection getNewConnection(HttpServletRequest request, String database) throws SQLException {
        return getNewConnection(request);
    }
}
