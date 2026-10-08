package com.billing.multitenancy;

import com.billing.core.AppDomains;
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
                domain = AppDomains.LOCAL_BACKEND_HOST;
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
        // Live tenant subdomain check — single shared domain constants in
        // core/AppDomains. Domain change ho to sirf wahi file badlo.
        boolean isTenantSubdomain = hostOnly != null && AppDomains.isLiveTenantSubdomain(hostOnly);

        // Maintenance stop (all modes, including local single-DB dev):
        // an INACTIVE tenant shows the maintenance screen everywhere.
        // Header first (frontend always sends X-Company-Code from its own
        // subdomain, and local dev calls the backend directly so Host is
        // just localhost:9009), then the Host subdomain. Unknown codes
        // (no registry row) keep their existing behavior below.
        // Platform paths are exempt: admin ops must work on INACTIVE tenants too.
        if (!isPlatformPath && companyRegistryService != null) {
            String maintenanceHeaderCode = resolveCompanyCodeHeader(request);
            if (maintenanceHeaderCode != null && !maintenanceHeaderCode.isBlank()) {
                String headerStatus = companyRegistryService.getStatusByCode(maintenanceHeaderCode.trim());
                if (headerStatus != null && !headerStatus.trim().isEmpty() && !"ACTIVE".equalsIgnoreCase(headerStatus.trim())) {
                    writeMaintenanceResponse(response, "TENANT_INACTIVE");
                    return false;
                }
            }
            String maintenanceSubdomain = resolveTenantSubdomain(hostOnly);
            if (maintenanceSubdomain != null && !maintenanceSubdomain.isBlank()) {
                String maintenanceStatus = companyRegistryService.getStatusByCode(maintenanceSubdomain);
                if (maintenanceStatus != null && !maintenanceStatus.trim().isEmpty() && !"ACTIVE".equalsIgnoreCase(maintenanceStatus.trim())) {
                    writeMaintenanceResponse(response, "TENANT_INACTIVE");
                    return false;
                }
            }
        }

        // Platform-admin is main-domain only: block it on tenant subdomains (403).
        if (isPlatformPath && isTenantSubdomain) {
            try {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Platform admin is available only on " + AppDomains.MAIN_DOMAIN);
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
        String companyCode = resolveCompanyCodeHeader(request);

        if (companyCode != null && !companyCode.trim().isEmpty() && companyRegistryService != null) {
            String dbFromCode = companyRegistryService.resolveDbName(companyCode.trim());
            if (dbFromCode != null && !dbFromCode.isEmpty()) {
                databaseName = dbFromCode;
                String dbHost = companyRegistryService.resolveDbHost(companyCode.trim());
                ensureTenantDataSource(databaseName, dbHost);
            }
        }

        // Maintenance stop: header points to an INACTIVE tenant (resolveDbName
        // only returns ACTIVE, so databaseName is still null here). Platform
        // paths are exempt: admin ops must work on INACTIVE tenants too.
        if (!isPlatformPath && databaseName == null && companyCode != null && !companyCode.trim().isEmpty() && companyRegistryService != null) {
            String headerStatus = companyRegistryService.getStatusByCode(companyCode.trim());
            if (headerStatus != null && !headerStatus.trim().isEmpty() && !"ACTIVE".equalsIgnoreCase(headerStatus.trim())) {
                writeMaintenanceResponse(response, "TENANT_INACTIVE");
                return false;
            }
        }

        // Ghost-tenant stop: X-Company-Code header names a code with NO registry
        // row at all (status blank). Never fall through to the default/sample DB —
        // that would silently log the user into the wrong database (local dev calls
        // the backend directly, so Host-based unknown-subdomain 404 below never fires
        // there). API/XHR calls get 404 so the frontend can show "unknown workspace".
        // No header at all (plain localhost, pre-login branding) keeps the old
        // fallback below — only a WRONG code is rejected here. Platform paths exempt.
        if (!isPlatformPath && databaseName == null && companyCode != null && !companyCode.trim().isEmpty()
                && companyRegistryService != null && requestUri != null && requestUri.startsWith("/api/")) {
            String headerStatus = companyRegistryService.getStatusByCode(companyCode.trim());
            if (headerStatus == null || headerStatus.trim().isEmpty()) {
                try {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Company not found");
                } catch (Exception ignored) {}
                return false;
            }
        }

        if (databaseName == null && isTenantSubdomain) {
            String subdomain = hostOnly.split("\\.")[0];
            if (subdomain != null && !subdomain.isBlank() && companyRegistryService != null) {
                String dbFromSubdomain = companyRegistryService.resolveDbName(subdomain);
                if (dbFromSubdomain == null || dbFromSubdomain.isBlank()) {
                    // Maintenance stop: subdomain is registered but INACTIVE.
                    // Unknown subdomains (no row at all) keep the old behavior below.
                    if (!isPlatformPath) {
                        String subStatus = companyRegistryService.getStatusByCode(subdomain);
                        if (subStatus != null && !subStatus.trim().isEmpty() && !"ACTIVE".equalsIgnoreCase(subStatus.trim())) {
                            writeMaintenanceResponse(response, "TENANT_INACTIVE");
                            return false;
                        }
                    }
                    // Unknown subdomain: browser page loads redirect to main site,
                    // API/XHR calls get 404 so the frontend can redirect itself.
                    if (requestUri != null && requestUri.startsWith("/api/")) {
                        try {
                            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Company not found");
                        } catch (Exception ignored) {}
                    } else {
                        try {
                            response.sendRedirect(AppDomains.MAIN_SITE_URL);
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
                } else if (!isPlatformPath && isTenantSubdomain) {
                    // Maintenance stop: full-domain row exists but is INACTIVE.
                    String domainStatus = companyRegistryService.getStatusByDomain(domain);
                    if (domainStatus != null && !domainStatus.trim().isEmpty() && !"ACTIVE".equalsIgnoreCase(domainStatus.trim())) {
                        writeMaintenanceResponse(response, "TENANT_INACTIVE");
                        return false;
                    }
                }
            }
        } // end domain resolution
        } // end live multi-tenant resolution (local single-DB assigned above)

        // Maintenance stop: registry says ACTIVE but the database is gone.
        // Platform paths are exempt: admin ops resolve their own tenant pools.
        if (!isPlatformPath && databaseName != null && !databaseName.isBlank()
                && !"billing_common".equalsIgnoreCase(databaseName.trim())
                && companyRegistryService != null && Registry.IS_ONLINE) {
            String dbHostForCheck = null;
            try {
                dbHostForCheck = companyRegistryService.resolveDbHostByDatabase(databaseName.trim());
            } catch (Exception ignored) {
                dbHostForCheck = null;
            }
            boolean poolMissing = dataSourcesMtApp != null && !dataSourcesMtApp.containsKey(databaseName.trim());
            boolean schemaMissing = isSameServerAsCommon(dbHostForCheck) && !companyRegistryService.databaseExists(databaseName.trim());
            if (poolMissing || schemaMissing) {
                writeMaintenanceResponse(response, "TENANT_UNAVAILABLE");
                return false;
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
                    response.sendRedirect(AppDomains.MAIN_SITE_URL);
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

    /**
     * Company code from header or request parameter, all modes.
     * Null when the caller sent none.
     */
    private String resolveCompanyCodeHeader(HttpServletRequest request) {
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
            return null;
        }
        return companyCode;
    }

    /**
     * Tenant subdomain from any host, for the maintenance stop:
     * sample.&lt;main-domain&gt; -> sample (live),
     * sample.localhost -> sample (local dev). Plain localhost,
     * 127.0.0.1 and the main domain return null.
     * Domain strings: core/AppDomains — do not hardcode here.
     */
    private String resolveTenantSubdomain(String hostOnly) {
        if (hostOnly == null || hostOnly.isBlank()) {
            return null;
        }
        String host = hostOnly.toLowerCase(java.util.Locale.ROOT);
        if (host.endsWith(AppDomains.LIVE_TENANT_SUFFIX)
                && !AppDomains.MAIN_DOMAIN.equalsIgnoreCase(host)
                && !AppDomains.WWW_MAIN_DOMAIN.equalsIgnoreCase(host)) {
            String sub = host.split("\\.")[0];
            if (sub != null && !sub.isBlank() && !"www".equalsIgnoreCase(sub)) {
                return sub;
            }
            return null;
        }
        if (host.endsWith(AppDomains.LOCAL_TENANT_SUFFIX) && !host.equalsIgnoreCase(AppDomains.LOCALHOST)) {
            String sub = host.split("\\.")[0];
            if (sub != null && !sub.isBlank() && !AppDomains.LOCALHOST.equalsIgnoreCase(sub)) {
                return sub;
            }
        }
        return null;
    }

    /**
     * Maintenance stop: INACTIVE tenant or missing database. No login, no
     * page and no API may proceed; the frontend shows a static screen.
     */
    private void writeMaintenanceResponse(HttpServletResponse response, String code) {
        try {
            TenantContextHolder.clear();
        } catch (Exception ignored) {
        }
        try {
            MDC.clear();
        } catch (Exception ignored) {
        }
        try {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"success\":false,\"message\":\"This workspace is temporarily unavailable. Please contact support.\",\"code\":\"" + code + "\"}");
        } catch (Exception ignored) {
        }
    }

    /**
     * billing_common always lives on localhost (BillingCatalogConfig), so a
     * schema-existence check via the common connection is only meaningful
     * when the tenant's db_host is the same server.
     */
    private boolean isSameServerAsCommon(String dbHost) {
        if (dbHost == null || dbHost.isBlank()) {
            return true;
        }
        String cleaned = dbHost.trim().replace("jdbc:mysql://", "").replace("/", "");
        String hostPart = cleaned.contains(":") ? cleaned.split(":")[0] : cleaned;
        return hostPart.equalsIgnoreCase(AppDomains.LOCALHOST) || hostPart.equals(AppDomains.LOCAL_LOOPBACK);
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
