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
        String requestUri = request.getRequestURI();
        // Error dispatches (from sendError) must pass through untouched, otherwise the
        // original status gets re-routed (e.g. a 404 wrongly becoming a 302 redirect).
        if (requestUri != null && (requestUri.equals("/error") || requestUri.startsWith("/error/"))) {
            return true;
        }
        // Host without port (local URLs carry :9009/:5173, live URLs carry no port).
        String hostOnly = domain;
        if (hostOnly != null && hostOnly.contains(":")) {
            hostOnly = hostOnly.split(":")[0];
        }
        boolean isPlatformPath = requestUri != null && requestUri.startsWith("/api/v1/platform-admin");
        boolean isTenantSubdomain = hostOnly != null && hostOnly.toLowerCase().endsWith(".biziotechnologies.com")
                && !hostOnly.equalsIgnoreCase("biziotechnologies.com")
                && !hostOnly.equalsIgnoreCase("www.biziotechnologies.com");

        // Platform-admin is main-domain only: block it on tenant subdomains (403).
        if (isPlatformPath && isTenantSubdomain) {
            try {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Platform admin is available only on biziotechnologies.com");
            } catch (Exception ignored) {}
            return false;
        }

        // Platform company-scoped routing: /api/v1/platform-admin/companies/{companyCode}/**
        // resolves the tenant DB via billing_common.company_registry.company_code (registry-gated).
        // Code-keyed: tenant-local numeric ids collide across databases (every tenant starts at 1).
        if (isPlatformPath && companyRegistryService != null) {
            String platformCompanyCode = extractPlatformCompanyCode(requestUri);
            if (platformCompanyCode != null) {
                String dbForCompany = companyRegistryService.resolveDbNameByCodeAnyStatus(platformCompanyCode);
                if (dbForCompany == null || dbForCompany.isBlank()
                        || !companyRegistryService.isValidDatabase(dbForCompany)) {
                    try {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Company not found");
                    } catch (Exception ignored) {}
                    return false;
                }
                databaseName = dbForCompany;
                ensureTenantDataSource(databaseName, companyRegistryService.resolveDbHostByDomain(domain));
            }
        }

        if (!Registry.IS_ONLINE) {
            // Local single-DB mode: tenant is ALWAYS the configured database
            // (Registry.dbmap "databasename" from MysqlDataSourceService).
            // Header/subdomain/domain are ignored for routing: single tenant
            // by construction, so cross-tenant routing is impossible locally.
            databaseName = Registry.dbmap.get("databasename");
        } else {
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

        if (databaseName == null && isTenantSubdomain) {
            String subdomain = hostOnly.split("\\.")[0];
            if (subdomain != null && !subdomain.isBlank() && companyRegistryService != null) {
                String dbFromSubdomain = companyRegistryService.resolveDbName(subdomain);
                if (dbFromSubdomain == null || dbFromSubdomain.isBlank()) {
                    // Unknown subdomain: browser page loads redirect to main site,
                    // API/XHR calls get 404 so the frontend can redirect itself.
                    if (requestUri != null && requestUri.startsWith("/api/")) {
                        try {
                            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Company not found");
                        } catch (Exception ignored) {}
                    } else {
                        try {
                            response.sendRedirect("https://biziotechnologies.com");
                        } catch (Exception ignored) {}
                    }
                    return false;
                }
                // Valid subdomain -> bind tenant DB immediately (TSM-like)
                databaseName = dbFromSubdomain;
                String dbHost = companyRegistryService.resolveDbHost(subdomain);
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
        } // end domain resolution
        } // end live multi-tenant resolution (local single-DB assigned above)

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
        if (databaseName == null && isPlatformPath) {
            // Platform root paths (login/dashboard/companies/settings on the main domain)
            // are served from the common catalog via JdbcTemplate and need no tenant pool.
            // Marker only: no pool exists for it, so accidental JPA access fails loud.
            databaseName = "billing_common";
        }
        // Token-binding: an authenticated USER/SUPER_ADMIN session may only touch
        // its own tenant database. The routing above is client-controlled
        // (header/subdomain/domain); the token claim is not. Mismatch -> 403.
        // Skipped for platform paths (path-based routing + @PreAuthorize) and for
        // anonymous calls (no token -> tokenTenant blank -> public endpoints pass).
        if (!isPlatformPath && databaseName != null && !databaseName.isBlank()
                && !"billing_common".equalsIgnoreCase(databaseName.trim())) {
            String tokenTenant = TenantContextHolder.getTenant();
            if (tokenTenant != null && !tokenTenant.isBlank()) {
                String boundDb = tokenTenant.replace("_read", "").replace("_write", "").trim();
                if (!boundDb.isEmpty() && !boundDb.equalsIgnoreCase(databaseName.trim())) {
                    try {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                "Tenant mismatch for this session. Please sign in again.");
                    } catch (Exception ignored) {}
                    return false;
                }
            }
        }
        if (databaseName == null || databaseName.isEmpty()) {
            // No silent default tenant: unknown host/domain is an explicit error.
            // Live -> main site; local -> 400 so misrouting never serves wrong company data.
            if (Registry.IS_ONLINE) {
                try {
                    response.sendRedirect("https://biziotechnologies.com");
                } catch (Exception ignored) {}
                return false;
            }
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Unable to resolve company database for host '" + domain + "'. Please contact administrator.");
            } catch (Exception ignored) {}
            return false;
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

    /**
     * Ensure WRITE+READ pools exist for a registry-listed tenant database
     * (used by platform provisioning/admin flows outside HTTP tenant routing).
     */
    public void ensureTenant(String tenant, String dbHost) {
        ensureTenantDataSource(tenant, dbHost);
    }

    /**
     * Extract companyCode from /api/v1/platform-admin/companies/{companyCode}/** paths.
     * Literal segments (overview) are NOT company codes. Codes are uppercase
     * alphanumeric (1-20 chars).
     */
    private String extractPlatformCompanyCode(String requestUri) {
        if (requestUri == null) {
            return null;
        }
        String[] seg = requestUri.split("/");
        // ["", "api", "v1", "platform-admin", "companies", "{companyCode}", ...]
        if (seg.length >= 6 && "api".equals(seg[1]) && "v1".equals(seg[2])
                && "platform-admin".equals(seg[3]) && "companies".equals(seg[4])) {
            String code = seg[5] == null ? "" : seg[5].trim().toUpperCase(java.util.Locale.ROOT);
            if (code.isEmpty() || "OVERVIEW".equals(code)) {
                return null;
            }
            if (!code.matches("[A-Z0-9]{1,20}")) {
                return null;
            }
            return code;
        }
        return null;
    }

    private synchronized void ensureTenantDataSourceWithHost(String tenant, String dbHost) {
        if (dataSourcesMtApp == null) {
            return;
        }
        // Registry gate: tenant pool is created ONLY if its database is registered
        // ACTIVE in billing_common.company_registry (common-DB-driven tenancy).
        if (tenant == null || tenant.isBlank() || "billing_common".equalsIgnoreCase(tenant.trim())) {
            return;
        }
        if (companyRegistryService != null && !companyRegistryService.isValidDatabase(tenant.trim())) {
            System.err.println("[WARN] Refusing tenant DataSource for unregistered database: " + tenant);
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
            if (com.billing.core.Registry.IS_ONLINE) {
                cfgR.setReadOnly(true);
            }
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
