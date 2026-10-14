package com.billing.service;

import com.billing.exception.BadRequestException;
import com.billing.multitenancy.RequestInterceptor;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.security.TenantSuperAdminPrincipal;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BizioConsole: tenant super-admin only SQL console.
 * Locked to the CURRENT tenant database (dynamic per subdomain, from
 * TenantContextHolder which RequestInterceptor binds via header/subdomain/domain).
 * No DB selector, no cross-DB access. SELECT/SHOW/DESCRIBE/EXPLAIN only.
 */
@Service
@RequiredArgsConstructor
public class BizioConsoleService {

    private static final int MAX_ROWS = 500;
    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final int MAX_SQL_LENGTH = 10000;

    private static final Pattern FIRST_WORD = Pattern.compile("^\\s*(\\w+)\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern QUALIFIED_NAME = Pattern.compile("[`\"\\[]?([A-Za-z0-9_]+)[`\"\\]]?\\s*\\.\\s*[`\"\\[]?[A-Za-z0-9_]+[`\"\\]]?");
    private static final Pattern LIMIT_CLAUSE = Pattern.compile("\\bLIMIT\\b", Pattern.CASE_INSENSITIVE);

    private static final String[] BANNED_KEYWORDS = {
            "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT",
            "DROP", "ALTER", "TRUNCATE", "CREATE",
            "GRANT", "REVOKE", "CALL", "EXEC", "EXECUTE",
            "HANDLER", "INSTALL", "UNINSTALL", "SHUTDOWN",
            "OUTFILE", "DUMPFILE", "LOAD_FILE", "LOAD DATA"
    };
    // NOTE: REPLACE deliberately not banned (REPLACE() string function is
    // read-only; REPLACE INTO is already blocked by first-word + ";" checks).

    private static final String[] BANNED_SCHEMAS = {
            "BILLING_COMMON", "MYSQL", "PERFORMANCE_SCHEMA", "SYS", "INFORMATION_SCHEMA"
    };

    private static final Logger log = LoggerFactory.getLogger(BizioConsoleService.class);

    @Autowired
    @Qualifier("dataSourcesMtApp")
    private Map<String, DataSource> dataSourcesMtApp;

    private final CompanyRegistryService companyRegistryService;
    private final RequestInterceptor requestInterceptor;

    @Transactional(readOnly = true)
    public Map<String, Object> execute(Map<String, Object> param, Authentication authentication) {
        try {
            requireSuperAdmin(authentication);
            String currentDb = resolveCurrentDb();
            String sql = DataTypeUtility.stringValue(param == null ? null : param.get("sql"));
            if (sql.isEmpty()) {
                throw new BadRequestException("SQL query is required");
            }
            if (sql.length() > MAX_SQL_LENGTH) {
                throw new BadRequestException("SQL query too long (max " + MAX_SQL_LENGTH + " chars)");
            }
            String finalSql = validateAndFinalize(sql, currentDb);
            JdbcTemplate jdbcTemplate = tenantJdbcTemplate(currentDb);
            long started = System.currentTimeMillis();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(finalSql);
            long executedInMs = System.currentTimeMillis() - started;
            boolean truncated = false;
            if (rows.size() > MAX_ROWS) {
                rows = new ArrayList<>(rows.subList(0, MAX_ROWS));
                truncated = true;
            }
            List<String> columns = new ArrayList<>();
            if (!rows.isEmpty() && rows.get(0) != null) {
                for (String key : rows.get(0).keySet()) {
                    columns.add(key);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("databaseName", currentDb);
            data.put("columns", columns);
            data.put("rows", rows);
            data.put("rowCount", rows.size());
            data.put("truncated", truncated);
            data.put("maxRows", MAX_ROWS);
            data.put("executedInMs", executedInMs);
            return data;
        } catch (BadRequestException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Query failed: " + safeErrorMessage(e));
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tables(Authentication authentication) {
        try {
            requireSuperAdmin(authentication);
            String currentDb = resolveCurrentDb();
            JdbcTemplate jdbcTemplate = tenantJdbcTemplate(currentDb);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME AS tableName FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME",
                    currentDb);
            List<String> tables = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String tableName = DataTypeUtility.stringValue(row == null ? null : row.get("tableName"));
                if (!tableName.isEmpty()) {
                    tables.add(tableName);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("databaseName", currentDb);
            data.put("tables", tables);
            data.put("tableCount", tables.size());
            return data;
        } catch (BadRequestException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to load tables: " + safeErrorMessage(e));
        }
    }

    private void requireSuperAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantSuperAdminPrincipal)) {
            throw new AccessDeniedException("BizioConsole is available only for super-admin login");
        }
    }

    private String resolveCurrentDb() {
        String tenant = TenantContextHolder.getTenant();
        if (tenant == null || tenant.isBlank()) {
            throw new BadRequestException("Unable to resolve company database for this request. Please contact administrator.");
        }
        String currentDb = tenant.replace("_read", "").replace("_write", "").trim();
        if (currentDb.isEmpty()) {
            throw new BadRequestException("Unable to resolve company database for this request. Please contact administrator.");
        }
        if ("billing_common".equalsIgnoreCase(currentDb)) {
            throw new AccessDeniedException("BizioConsole is not available on this host");
        }
        return currentDb;
    }

    private JdbcTemplate tenantJdbcTemplate(String currentDb) {
        if (dataSourcesMtApp == null || dataSourcesMtApp.isEmpty()) {
            log.warn("[BizioConsole] tenant pool map unavailable for db '{}'", currentDb);
            throw new BadRequestException("Tenant database is not available. Please contact administrator.");
        }
        DataSource dataSource = dataSourcesMtApp.get(currentDb);
        if (dataSource == null) {
            dataSource = dataSourcesMtApp.get(currentDb + "_read");
        }
        if (dataSource == null) {
            // Lazy pools are created on first HTTP request; a restarted backend
            // or a never-visited tenant may simply not have its pool yet.
            // Trigger on-demand creation, then look again.
            try {
                String dbHost = null;
                try {
                    dbHost = companyRegistryService.resolveDbHostByDatabase(currentDb);
                } catch (Exception ignored) {
                    dbHost = null;
                }
                requestInterceptor.ensureTenant(currentDb, dbHost);
            } catch (Exception e) {
                log.warn("[BizioConsole] ensureTenant failed for db '{}': {}", currentDb, e.getMessage());
            }
            dataSource = dataSourcesMtApp.get(currentDb);
            if (dataSource == null) {
                dataSource = dataSourcesMtApp.get(currentDb + "_read");
            }
        }
        if (dataSource == null) {
            log.warn("[BizioConsole] no pool for db '{}' (pools: {})", currentDb, dataSourcesMtApp.size());
            throw new BadRequestException("Tenant database is not available. Please contact administrator.");
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        jdbcTemplate.setMaxRows(MAX_ROWS + 1);
        return jdbcTemplate;
    }

    private String validateAndFinalize(String sql, String currentDb) {
        String stripped = sql.trim();
        while (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        if (stripped.isEmpty()) {
            throw new BadRequestException("SQL query is required");
        }
        if (stripped.contains(";")) {
            throw new BadRequestException("Multiple statements are not allowed. Run one query at a time.");
        }
        if (stripped.contains("--") || stripped.contains("/*") || stripped.contains("*/") || stripped.contains("#")) {
            throw new BadRequestException("SQL comments are not allowed");
        }
        Matcher firstWord = FIRST_WORD.matcher(stripped);
        if (!firstWord.find()) {
            throw new BadRequestException("Only SELECT, SHOW, DESCRIBE and EXPLAIN queries are allowed");
        }
        String first = firstWord.group(1).toUpperCase(Locale.ROOT);
        if (!("SELECT".equals(first) || "SHOW".equals(first) || "DESCRIBE".equals(first)
                || "DESC".equals(first) || "EXPLAIN".equals(first))) {
            throw new BadRequestException("Only SELECT, SHOW, DESCRIBE and EXPLAIN queries are allowed");
        }
        String upper = stripped.toUpperCase(Locale.ROOT);
        for (String banned : BANNED_KEYWORDS) {
            if (containsWord(upper, banned)) {
                throw new BadRequestException("Only read-only SELECT queries are allowed (" + banned + " is blocked)");
            }
        }
        if (upper.matches("(?s).*\\bUSE\\b.*")) {
            throw new BadRequestException("USE statement is not allowed. Console is locked to the current database.");
        }
        if (upper.matches("(?s).*\\bINTO\\b.*")) {
            throw new BadRequestException("Only read-only SELECT queries are allowed (INTO is blocked)");
        }
        for (String schema : BANNED_SCHEMAS) {
            if (upper.contains(schema)) {
                throw new BadRequestException("Query is locked to the current database. Cross-database access is not allowed.");
            }
        }
        Matcher qualifier = QUALIFIED_NAME.matcher(stripStringLiterals(stripped));
        while (qualifier.find()) {
            String qualifierDb = qualifier.group(1);
            if (qualifierDb == null || qualifierDb.isBlank()
                    || qualifierDb.equalsIgnoreCase(currentDb)) {
                continue;
            }
            // Table aliases (u.name) and decimals (1.5) look identical to
            // db.table — block ONLY when the qualifier is a real registered
            // database. System schemas are already blocked above by substring.
            boolean knownDb = false;
            try {
                knownDb = companyRegistryService.isValidDatabase(qualifierDb.trim());
            } catch (Exception ignored) {
                knownDb = false;
            }
            if (knownDb) {
                throw new BadRequestException("Query is locked to the current database (" + currentDb + "). Cross-database access is not allowed.");
            }
        }
        if ("SELECT".equals(first) && !LIMIT_CLAUSE.matcher(stripped).find()) {
            stripped = stripped + " LIMIT " + MAX_ROWS;
        }
        return stripped;
    }

    private boolean containsWord(String upper, String keyword) {
        if (keyword.contains(" ")) {
            return upper.contains(keyword);
        }
        return upper.matches("(?s).*\\b" + Pattern.quote(keyword) + "\\b.*");
    }

    private String stripStringLiterals(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        String noSingle = sql.replaceAll("'(?:[^'\\\\]|\\\\.)*'", "''");
        return noSingle.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
    }

    private String safeErrorMessage(Exception e) {
        String message = e == null ? "" : DataTypeUtility.stringValue(e.getMessage());
        if (message.isEmpty()) {
            message = "unexpected error";
        }
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        return message;
    }
}
