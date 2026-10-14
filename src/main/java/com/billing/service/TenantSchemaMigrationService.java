package com.billing.service;

import com.billing.core.Registry;
import com.billing.multitenancy.RequestInterceptor;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileCopyUtils;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deploy-time schema migrator for DATABASE-per-tenant setup.
 * Live runs with hbm2ddl=validate, so new tables/columns from entities never
 * reach tenant DBs automatically. This runner applies the versioned
 * {@code db/migration/V*.sql} files (same files we used to run by hand)
 * to every ACTIVE tenant database on boot, plus common-target files once
 * to billing_common. Progress is tracked per database in schema_version,
 * so already-applied files never re-run.
 *
 * File target convention (first lines of the file):
 *   -- target: common  -> billing_common only (once)
 *   -- target: tenant  -> every ACTIVE tenant database (default)
 * Files without a marker fall back to text detection (mentions
 * billing_common.) for the two legacy common files.
 *
 * Supported statements: plain DDL/DML separated by ';'. Stored
 * procedures/triggers needing DELIMITER are NOT supported and fail fast
 * with a clear message instead of half-applying.
 */
@Service
@RequiredArgsConstructor
public class TenantSchemaMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaMigrationService.class);

    private static final String MIGRATION_LOCATION = "classpath*:db/migration/V*.sql";
    private static final int STATEMENT_TIMEOUT_SECONDS = 120;

    private final CompanyRegistryService companyRegistryService;
    private final RequestInterceptor requestInterceptor;

    @Autowired
    @Qualifier("dataSourcesMtApp")
    private Map<String, DataSource> dataSourcesMtApp;

    @Autowired
    @Qualifier("billingCommonJdbcTemplate")
    private JdbcTemplate billingCommonJdbcTemplate;

    /**
     * Migrate billing_common (once) + every ACTIVE tenant database.
     * Never throws: per-database failures are collected in the result.
     */
    public List<Map<String, Object>> migrateAll() {
        List<Map<String, Object>> results = new ArrayList<>();
        List<MigrationFile> files = loadMigrationFiles();
        if (files.isEmpty()) {
            log.warn("[Migrate] no migration files found at {}", MIGRATION_LOCATION);
            return results;
        }
        List<MigrationFile> commonFiles = new ArrayList<>();
        List<MigrationFile> tenantFiles = new ArrayList<>();
        for (MigrationFile file : files) {
            if (file.isCommonTarget()) {
                commonFiles.add(file);
            } else {
                tenantFiles.add(file);
            }
        }
        results.add(migrateDatabase("billing_common", "COMMON", null, commonFiles));
        List<Map<String, Object>> tenants = activeTenants();
        for (Map<String, Object> tenant : tenants) {
            String code = DataTypeUtility.stringValue(tenant.get("company_code"));
            String databaseName = DataTypeUtility.stringValue(tenant.get("database_name"));
            String dbHost = DataTypeUtility.stringNullValue(tenant.get("db_host"));
            if (databaseName.isEmpty()) {
                continue;
            }
            Map<String, Object> result = migrateTenantDatabase(databaseName, dbHost, tenantFiles);
            result.put("companyCode", code.isEmpty() ? null : code.toUpperCase(java.util.Locale.ROOT));
            results.add(result);
        }
        if (tenants.isEmpty() && !Registry.IS_ONLINE) {
            // Local single-DB dev without registry rows: migrate the one
            // configured database so hand-running files locally ends too.
            String fallbackDb = Registry.dbmap.get("databasename");
            if (fallbackDb != null && !fallbackDb.isBlank()
                    && !"billing_common".equalsIgnoreCase(fallbackDb.trim())) {
                results.add(migrateTenantDatabase(fallbackDb.trim(), null, tenantFiles));
            }
        }
        return results;
    }

    /**
     * Migrate one tenant database (pool created on demand if missing).
     * Never throws: failures are reported in the result map.
     */
    public Map<String, Object> migrateTenantDatabase(String databaseName, String dbHost, List<MigrationFile> files) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseName", databaseName);
        result.put("scope", "TENANT");
        List<String> applied = new ArrayList<>();
        List<String> alreadyApplied = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        result.put("applied", applied);
        result.put("alreadyApplied", alreadyApplied);
        result.put("failed", failed);
        if (databaseName == null || databaseName.isBlank() || "billing_common".equalsIgnoreCase(databaseName.trim())) {
            result.put("status", "SKIPPED");
            result.put("detail", "Not a tenant database");
            return result;
        }
        String db = databaseName.trim();
        try {
            requestInterceptor.ensureTenant(db, dbHost);
            DataSource dataSource = tenantWriteDataSource(db);
            if (dataSource == null) {
                result.put("status", "SKIPPED");
                result.put("detail", "Tenant pool unavailable");
                log.warn("[Migrate] SKIPPED db='{}': tenant pool unavailable", db);
                return result;
            }
            List<MigrationFile> pending = files == null ? loadMigrationFiles() : files;
            List<MigrationFile> tenantPending = new ArrayList<>();
            for (MigrationFile file : pending) {
                if (!file.isCommonTarget()) {
                    tenantPending.add(file);
                }
            }
            JdbcTemplate jdbcTemplate = tenantJdbcTemplate(dataSource);
            applyPending(jdbcTemplate, db, tenantPending, applied, alreadyApplied, failed);
            if (!failed.isEmpty()) {
                result.put("status", "FAILED");
                result.put("detail", "Stopped at " + failed.get(0));
            } else {
                result.put("status", "OK");
                result.put("detail", "applied=" + applied.size() + " alreadyApplied=" + alreadyApplied.size());
            }
            log.warn("[Migrate] {} db='{}' applied={} alreadyApplied={} failed={}",
                    result.get("status"), db, applied.size(), alreadyApplied.size(), failed.size());
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("detail", safeMessage(e));
            log.warn("[Migrate] FAILED db='{}': {}", db, safeMessage(e));
        }
        return result;
    }

    private Map<String, Object> migrateDatabase(String databaseName, String scope, String dbHost, List<MigrationFile> files) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseName", databaseName);
        result.put("scope", scope);
        List<String> applied = new ArrayList<>();
        List<String> alreadyApplied = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        result.put("applied", applied);
        result.put("alreadyApplied", alreadyApplied);
        result.put("failed", failed);
        try {
            JdbcTemplate jdbcTemplate = billingCommonJdbcTemplate;
            jdbcTemplate.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
            applyPending(jdbcTemplate, databaseName, files, applied, alreadyApplied, failed);
            if (!failed.isEmpty()) {
                result.put("status", "FAILED");
                result.put("detail", "Stopped at " + failed.get(0));
            } else {
                result.put("status", "OK");
                result.put("detail", "applied=" + applied.size() + " alreadyApplied=" + alreadyApplied.size());
            }
            log.warn("[Migrate] {} db='{}' applied={} alreadyApplied={} failed={}",
                    result.get("status"), databaseName, applied.size(), alreadyApplied.size(), failed.size());
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("detail", safeMessage(e));
            log.warn("[Migrate] FAILED db='{}': {}", databaseName, safeMessage(e));
        }
        return result;
    }

    private void applyPending(JdbcTemplate jdbcTemplate, String databaseName, List<MigrationFile> files,
                              List<String> applied, List<String> alreadyApplied, List<String> failed) {
        ensureVersionTable(jdbcTemplate);
        Set<String> done = appliedVersions(jdbcTemplate);
        for (MigrationFile file : files) {
            if (done.contains(file.getVersion())) {
                alreadyApplied.add(file.getVersion());
                continue;
            }
            List<String> statements = splitStatements(file.getSql());
            if (statements.isEmpty()) {
                recordVersion(jdbcTemplate, file, true);
                alreadyApplied.add(file.getVersion());
                continue;
            }
            try {
                TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
                tx.setTimeout(STATEMENT_TIMEOUT_SECONDS);
                tx.executeWithoutResult(status -> {
                    for (String statement : statements) {
                        jdbcTemplate.execute(statement);
                    }
                });
                recordVersion(jdbcTemplate, file, true);
                applied.add(file.getVersion());
                log.warn("[Migrate] applied db='{}' version='{}' ({} statements)",
                        databaseName, file.getVersion(), statements.size());
            } catch (Exception e) {
                if (isAlreadyExistsError(e)) {
                    // Fresh databases built from tenant_schema.sql already contain
                    // old schema changes: tolerate duplicate table/column/key.
                    recordVersion(jdbcTemplate, file, true);
                    alreadyApplied.add(file.getVersion());
                    log.warn("[Migrate] tolerated (already exists) db='{}' version='{}': {}",
                            databaseName, file.getVersion(), safeMessage(e));
                } else {
                    failed.add(file.getVersion());
                    recordVersion(jdbcTemplate, file, false);
                    log.warn("[Migrate] FAILED db='{}' version='{}': {}",
                            databaseName, file.getVersion(), safeMessage(e));
                    return;
                }
            }
        }
    }

    private void ensureVersionTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                + "version VARCHAR(255) NOT NULL, "
                + "description VARCHAR(500) NULL, "
                + "applied_at DATETIME(6) NOT NULL, "
                + "success BIT(1) NOT NULL DEFAULT b'1', "
                + "PRIMARY KEY (version)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    private Set<String> appliedVersions(JdbcTemplate jdbcTemplate) {
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT version FROM schema_version WHERE success = b'1'", String.class);
            return new LinkedHashSet<>(rows);
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    private void recordVersion(JdbcTemplate jdbcTemplate, MigrationFile file, boolean success) {
        try {
            jdbcTemplate.update("INSERT INTO schema_version (version, description, applied_at, success)"
                            + " VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
                            + " description = VALUES(description), applied_at = VALUES(applied_at), success = VALUES(success)",
                    file.getVersion(), file.getDescription(), LocalDateTime.now(), success);
        } catch (Exception e) {
            log.warn("[Migrate] could not record version '{}': {}", file.getVersion(), safeMessage(e));
        }
    }

    private boolean isAlreadyExistsError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx) {
                int code = sqlEx.getErrorCode();
                if (code == 1050 || code == 1060 || code == 1061) {
                    return true;
                }
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("already exists") || lower.contains("duplicate column")
                        || lower.contains("duplicate key name")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private List<Map<String, Object>> activeTenants() {
        try {
            return companyRegistryService.listTenants(null, "ACTIVE", 100000, 0);
        } catch (Exception e) {
            log.warn("[Migrate] could not list registry tenants: {}", safeMessage(e));
            return new ArrayList<>();
        }
    }

    private DataSource tenantWriteDataSource(String databaseName) {
        if (dataSourcesMtApp == null || dataSourcesMtApp.isEmpty()) {
            return null;
        }
        return dataSourcesMtApp.get(databaseName);
    }

    private JdbcTemplate tenantJdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
        return jdbcTemplate;
    }

    private List<MigrationFile> loadMigrationFiles() {
        List<MigrationFile> files = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(MIGRATION_LOCATION);
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".sql")) {
                    continue;
                }
                String sql;
                try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    sql = FileCopyUtils.copyToString(reader);
                }
                if (sql == null || sql.isBlank()) {
                    continue;
                }
                files.add(MigrationFile.from(filename, sql));
            }
        } catch (Exception e) {
            log.warn("[Migrate] could not scan migration files: {}", safeMessage(e));
        }
        return files;
    }

    /**
     * Splits a migration file into single statements on ';' outside string
     * literals. Strips block comments and full-line '--' comments.
     * DELIMITER changes (procedures/triggers) are not supported.
     */
    private List<String> splitStatements(String sql) {
        String noBlocks = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        if (noBlocks.toUpperCase(java.util.Locale.ROOT).contains("DELIMITER")) {
            throw new IllegalStateException("DELIMITER-based scripts (procedures/triggers) are not supported by the auto-migrator. Run this file by hand.");
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        char[] chars = noBlocks.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    current.append('\n');
                }
                continue;
            }
            if (!inSingle && !inDouble && c == '-' && i + 2 < chars.length
                    && chars[i + 1] == '-' && (chars[i + 2] == ' ' || chars[i + 2] == '\t'
                    || chars[i + 2] == '\n' || chars[i + 2] == '\r')) {
                inLineComment = true;
                continue;
            }
            if (c == '\'' && !inDouble) {
                if (i + 1 < chars.length && chars[i + 1] == '\'') {
                    current.append("''");
                    i++;
                    continue;
                }
                inSingle = !inSingle;
                current.append(c);
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(c);
                continue;
            }
            if (c == '\\' && (inSingle || inDouble) && i + 1 < chars.length) {
                current.append(c).append(chars[i + 1]);
                i++;
                continue;
            }
            if (c == ';' && !inSingle && !inDouble) {
                String statement = current.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    private String safeMessage(Exception e) {
        String message = e == null ? "" : DataTypeUtility.stringValue(e.getMessage());
        if (message.isEmpty()) {
            message = e == null ? "unknown error" : e.getClass().getSimpleName();
        }
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        return message;
    }

    /**
     * One versioned migration file. Version = filename without .sql
     * (V-prefix sorts chronologically, applied in that order).
     */
    public static class MigrationFile {
        private final String version;
        private final String description;
        private final String sql;
        private final boolean commonTarget;

        private MigrationFile(String version, String description, String sql, boolean commonTarget) {
            this.version = version;
            this.description = description;
            this.sql = sql;
            this.commonTarget = commonTarget;
        }

        public static MigrationFile from(String filename, String sql) {
            String version = filename.substring(0, filename.length() - 4);
            String core = version.startsWith("V") ? version.substring(1) : version;
            int first = core.indexOf('_');
            int second = first < 0 ? -1 : core.indexOf('_', first + 1);
            String description = second < 0 ? core.replace('_', ' ') : core.substring(second + 1).replace('_', ' ');
            boolean commonTarget = isCommonTarget(sql);
            return new MigrationFile(version, description, sql, commonTarget);
        }

        private static boolean isCommonTarget(String sql) {
            String[] lines = sql.split("\n");
            int checked = 0;
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("-- target:")) {
                    return trimmed.toLowerCase(java.util.Locale.ROOT).contains("common");
                }
                checked++;
                if (checked >= 5) {
                    break;
                }
            }
            return sql.contains("billing_common.");
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }

        public String getSql() {
            return sql;
        }

        public boolean isCommonTarget() {
            return commonTarget;
        }
    }
}
