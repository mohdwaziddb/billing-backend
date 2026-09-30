package com.billing.multitenancy;

import com.billing.core.CompanyDomainInfo;
import com.billing.core.Registry;
import com.billing.core.appconfig.ApplicationConstant;
import com.billing.service.CompanyRegistryService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.sql.DataSource;
import java.util.Enumeration;
import java.util.Map;

@Component
public class RequestInterceptor implements HandlerInterceptor {

    @Autowired(required = false)
    private CompanyRegistryService companyRegistryService;

    @Autowired(required = false)
    @Qualifier("dataSourcesMtApp")
    private Map<String, DataSource> dataSourcesMtApp;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUrl = request.getRequestURL().toString();
        String domain = "";
        try {
            domain = requestUrl.split("/")[2];
        } catch (Exception e) {
            domain = request.getHeader("Host");
            if (domain == null) {
                domain = "localhost:9009";
            }
        }

        String databaseName = null;

        String companyCode = request.getHeader("X-Company-Code");
        if (companyCode == null || companyCode.trim().isEmpty()) {
            companyCode = request.getHeader("x-company-code");
        }
        if (companyCode == null || companyCode.trim().isEmpty()) {
            companyCode = request.getHeader("company_code");
        }
        if (companyCode == null || companyCode.trim().isEmpty()) {
            companyCode = request.getParameter("company_code");
        }
        if (companyCode == null || companyCode.trim().isEmpty()) {
            companyCode = request.getParameter("companyCode");
        }
        if (companyCode == null || companyCode.trim().isEmpty()) {
            companyCode = request.getParameter("companyCode");
        }

        if (companyCode != null && !companyCode.trim().isEmpty() && companyRegistryService != null) {
            String dbFromCode = companyRegistryService.resolveDbName(companyCode.trim());
            if (dbFromCode != null && !dbFromCode.isEmpty()) {
                databaseName = dbFromCode;
                String dbHost = companyRegistryService.resolveDbHost(companyCode.trim());
                ensureTenantDataSource(databaseName, dbHost);
            }
        }

        if (databaseName == null) {
            String domainDb = CompanyDomainInfo.getDatabaseName(domain);
            if (domainDb != null && !domainDb.isEmpty()) {
                databaseName = domainDb;
                String dbHost = null;
                try {
                    if (CompanyDomainInfo.domainInfoJSON.containsKey(domain)) {
                        dbHost = CompanyDomainInfo.domainInfoJSON.get(domain).optString("databaseip").replace("jdbc:mysql://", "").replace("/", "");
                    }
                } catch (Exception e) {
                    dbHost = Registry.dbmap.get("url");
                }
                if (dbHost == null || dbHost.isBlank()) {
                    dbHost = Registry.dbmap.get("url");
                }
                ensureTenantDataSource(databaseName, dbHost);
            } else if (companyRegistryService != null) {
                String dbFromDomain = companyRegistryService.resolveDbNameByDomain(domain);
                if (dbFromDomain != null && !dbFromDomain.isEmpty()) {
                    databaseName = dbFromDomain;
                    String dbHost = companyRegistryService.resolveDbHostByDomain(domain);
                    ensureTenantDataSource(databaseName, dbHost);
                }
            }
        }

        if (databaseName == null) {
            if (Registry.IS_ONLINE) {
                databaseName = CompanyDomainInfo.getDatabaseName(domain);
                if (databaseName == null) {
                    databaseName = Registry.dbmap.get("databasename");
                }
            } else {
                databaseName = Registry.dbmap.get("databasename");
            }
        }
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = "billing_common";
        }

        Enumeration<String> headers = request.getHeaders(ApplicationConstant.CONNECTION_TYPE_PARAM_NAME);
        String connectionType = null;
        if (headers.hasMoreElements()) {
            connectionType = headers.nextElement();
        }

        request.setAttribute(ApplicationConstant.REQUEST_DATABASE_NAME, databaseName);
        request.setAttribute(ApplicationConstant.DATABASE_NAME, databaseName);

        if (connectionType != null && !connectionType.isEmpty()) {
            if (connectionType.equalsIgnoreCase(ApplicationConstant.CONNECTION_READ_WRITE_STRING)) {
                connectionType = ApplicationConstant.CONNECTION_WRITE_STRING;
            } else if (connectionType.equalsIgnoreCase(ApplicationConstant.CONNECTION_WRITE_STRING)) {
                connectionType = ApplicationConstant.CONNECTION_WRITE_STRING;
            } else if (connectionType.equalsIgnoreCase(ApplicationConstant.CONNECTION_READ_STRING)) {
                connectionType = ApplicationConstant.CONNECTION_READ_STRING;
            }
        } else if (requestUrl != null && requestUrl.contains("/billing_common/")) {
            connectionType = ApplicationConstant.CONNECTION_WRITE_STRING;
        } else {
            String methodName = request.getMethod();
            if (methodName != null && methodName.equalsIgnoreCase("get")) {
                connectionType = ApplicationConstant.CONNECTION_READ_STRING;
            } else {
                connectionType = ApplicationConstant.CONNECTION_WRITE_STRING;
            }
        }

        if (connectionType != null && connectionType.equalsIgnoreCase(ApplicationConstant.CONNECTION_WRITE_STRING)) {
            TenantContextHolder.setTenantId(databaseName);
        } else {
            TenantContextHolder.setTenantId(databaseName + ApplicationConstant.CONNECTION_READ_STRING);
        }

        MDC.clear();
        MDC.put("url", domain);
        return true;
    }

    private synchronized void ensureTenantDataSource(String tenant, String dbHost) {
        ensureTenantDataSourceWithHost(tenant, dbHost);
    }

    private synchronized void ensureTenantDataSourceWithHost(String tenant, String dbHost) {
        if (dataSourcesMtApp == null) {
            return;
        }
        if (dataSourcesMtApp.containsKey(tenant) && dataSourcesMtApp.containsKey(tenant + ApplicationConstant.CONNECTION_READ_STRING)) {
            return;
        }
        try {
            String hostPort = dbHost;
            if (hostPort == null || hostPort.trim().isEmpty()) {
                hostPort = Registry.dbmap.getOrDefault("url", "localhost:3306");
            }
            hostPort = hostPort.trim().replace("jdbc:mysql://", "").replace("/", "");
            String jdbcBase = "jdbc:mysql://" + hostPort + "/";
            String username = Registry.dbmap.getOrDefault("username", "root");
            String password = Registry.dbmap.getOrDefault("password", "root");
            String driver = Registry.dbmap.getOrDefault("driverClassName", "com.mysql.cj.jdbc.Driver");

            HikariConfig cfgW = new HikariConfig();
            cfgW.setJdbcUrl(jdbcBase + tenant);
            cfgW.setUsername(username);
            cfgW.setPassword(password);
            cfgW.setDriverClassName(driver);
            cfgW.setMinimumIdle(0);
            cfgW.setMaximumPoolSize(20);
            cfgW.setPoolName(tenant + "-WRITE");
            cfgW.setConnectionTimeout(10000);
            cfgW.setIdleTimeout(60000);

            HikariConfig cfgR = new HikariConfig();
            cfgR.setJdbcUrl(jdbcBase + tenant);
            cfgR.setUsername(username);
            cfgR.setPassword(password);
            cfgR.setDriverClassName(driver);
            cfgR.setMinimumIdle(0);
            cfgR.setMaximumPoolSize(20);
            cfgR.setPoolName(tenant + "-READ");
            cfgR.setReadOnly(true);
            cfgR.setConnectionTimeout(10000);

            if (!dataSourcesMtApp.containsKey(tenant)) {
                dataSourcesMtApp.put(tenant, new HikariDataSource(cfgW));
            }
            if (!dataSourcesMtApp.containsKey(tenant + ApplicationConstant.CONNECTION_READ_STRING)) {
                dataSourcesMtApp.put(tenant + ApplicationConstant.CONNECTION_READ_STRING, new HikariDataSource(cfgR));
            }
            System.out.println("[INFO] Lazy initialized tenant DataSources for: " + tenant);
        } catch (Exception e) {
            System.err.println("[WARN] Failed to lazy init DataSource for tenant " + tenant + ": " + e.getMessage());
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantContextHolder.clear();
        MDC.clear();
    }
}
