package com.billing.service;

import com.billing.core.Registry;
import com.billing.multitenancy.RequestInterceptor;
import com.billing.util.DataTypeUtility;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Fail-loud schema guard: compares the Hibernate entity metamodel (exact
 * runtime table/column names, naming strategy applied) against
 * INFORMATION_SCHEMA for billing_common + every ACTIVE tenant database.
 *
 * Why this exists: live runs hbm2ddl=none, so an entity change whose
 * migration never applied (file missing from the jar, runner skipped the
 * tenant, failed ALTER) passes boot silently and explodes as 500s on the
 * first query touching the drifted table (e.g. "Unknown column
 * 'nl1_0.sender'"). This verifier runs at the end of every boot (after
 * migrate + seeds) and on demand via repair-all, and SHOUTS per database
 * instead of letting users discover the drift.
 *
 * Scope: tenant DBs check every entity table except platform_settings
 * (common-catalog table, checked against billing_common instead).
 * Read-only: never creates, alters, or migrates anything. Never throws.
 */
@Service
@RequiredArgsConstructor
public class TenantSchemaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaVerifier.class);

    private static final String COMMON_SETTINGS_TABLE = "platform_settings";

    private final CompanyRegistryService companyRegistryService;
    private final RequestInterceptor requestInterceptor;
    private final EntityManagerFactory entityManagerFactory;

    @Autowired
    @Qualifier("dataSourcesMtApp")
    private Map<String, DataSource> dataSourcesMtApp;

    @Autowired
    @Qualifier("billingCommonJdbcTemplate")
    private JdbcTemplate billingCommonJdbcTemplate;

    /**
     * Verify billing_common + every ACTIVE tenant database.
     * Never throws: per-database failures are collected in the result.
     */
    public List<Map<String, Object>> verifyAll() {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, java.util.Set<String>> expected;
        try {
            expected = expectedSchema();
        } catch (Exception e) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("scope", "ALL");
            failure.put("status", "SKIPPED");
            failure.put("detail", "Could not read entity metamodel: " + safeMessage(e));
            log.error("[Verify] SKIPPED: could not read entity metamodel: {}", safeMessage(e));
            results.add(failure);
            return results;
        }
        if (expected.isEmpty()) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("scope", "ALL");
            failure.put("status", "SKIPPED");
            failure.put("detail", "Entity metamodel is empty");
            log.error("[Verify] SKIPPED: entity metamodel is empty");
            results.add(failure);
            return results;
        }
        results.add(verifyDatabase("billing_common", "COMMON", expectedColumnsFor(expected, true)));
        List<Map<String, Object>> tenants = activeTenants();
        if (tenants.isEmpty() && Registry.IS_ONLINE) {
            log.error("[Verify] registry returned ZERO active tenants — tenant verification SKIPPED. Check billing_common.company_registry rows/status and DB privileges.");
        }
        Map<String, java.util.Set<String>> tenantExpected = expectedColumnsFor(expected, false);
        for (Map<String, Object> tenant : tenants) {
            String code = DataTypeUtility.stringValue(tenant.get("company_code"));
            String databaseName = DataTypeUtility.stringValue(tenant.get("database_name"));
            String dbHost = DataTypeUtility.stringNullValue(tenant.get("db_host"));
            if (databaseName.isEmpty()) {
                continue;
            }
            Map<String, Object> result = verifyTenantDatabase(databaseName.trim(), dbHost, tenantExpected);
            result.put("companyCode", code.isEmpty() ? null : code.toUpperCase(Locale.ROOT));
            results.add(result);
        }
        return results;
    }

    /**
     * Tenant-scope missing objects for one database/connection, or null when
     * the check itself cannot run (pool/privilege failure). Used by the
     * auto-DDL service for before/after diffing. Never throws.
     */
    public List<String> missingForDatabase(String databaseName, DataSource dataSource) {
        try {
            Map<String, java.util.Set<String>> expected = expectedColumnsFor(expectedSchema(), false);
            if (expected.isEmpty()) {
                return null;
            }
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.setQueryTimeout(60);
            Map<String, java.util.Set<String>> actual = actualSchema(jdbcTemplate, databaseName);
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, java.util.Set<String>> entry : expected.entrySet()) {
                java.util.Set<String> actualColumns = actual.get(entry.getKey());
                if (actualColumns == null) {
                    missing.add(entry.getKey());
                    continue;
                }
                for (String column : entry.getValue()) {
                    if (!actualColumns.contains(column)) {
                        missing.add(entry.getKey() + "." + column);
                    }
                }
            }
            return missing;
        } catch (Exception e) {
            log.warn("[Verify] missing-check failed db='{}': {}", databaseName, safeMessage(e));
            return null;
        }
    }

    private Map<String, Object> verifyTenantDatabase(String databaseName, String dbHost,
                                                      Map<String, java.util.Set<String>> expected) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseName", databaseName);
        result.put("scope", "TENANT");
        try {
            requestInterceptor.ensureTenant(databaseName, dbHost);
            DataSource dataSource = dataSourcesMtApp == null ? null : dataSourcesMtApp.get(databaseName);
            if (dataSource == null && dataSourcesMtApp != null) {
                dataSource = dataSourcesMtApp.get(databaseName + "_read");
            }
            if (dataSource == null) {
                result.put("status", "SKIPPED");
                result.put("detail", "Tenant pool unavailable");
                log.warn("[Verify] SKIPPED db='{}': tenant pool unavailable", databaseName);
                return result;
            }
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.setQueryTimeout(60);
            return diffSchema(jdbcTemplate, databaseName, "TENANT", expected, result);
        } catch (Exception e) {
            result.put("status", "SKIPPED");
            result.put("detail", safeMessage(e));
            log.warn("[Verify] SKIPPED db='{}': {}", databaseName, safeMessage(e));
            return result;
        }
    }

    private Map<String, Object> verifyDatabase(String databaseName, String scope,
                                                Map<String, java.util.Set<String>> expected) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseName", databaseName);
        result.put("scope", scope);
        try {
            JdbcTemplate jdbcTemplate = billingCommonJdbcTemplate;
            jdbcTemplate.setQueryTimeout(60);
            return diffSchema(jdbcTemplate, databaseName, scope, expected, result);
        } catch (Exception e) {
            result.put("status", "SKIPPED");
            result.put("detail", safeMessage(e));
            log.warn("[Verify] SKIPPED db='{}': {}", databaseName, safeMessage(e));
            return result;
        }
    }

    private Map<String, Object> diffSchema(JdbcTemplate jdbcTemplate, String databaseName, String scope,
                                           Map<String, java.util.Set<String>> expected,
                                           Map<String, Object> result) {
        Map<String, java.util.Set<String>> actual = actualSchema(jdbcTemplate, databaseName);
        List<String> missingTables = new ArrayList<>();
        List<String> missingColumns = new ArrayList<>();
        int checkedColumns = 0;
        for (Map.Entry<String, java.util.Set<String>> entry : expected.entrySet()) {
            String table = entry.getKey();
            java.util.Set<String> actualColumns = actual.get(table);
            if (actualColumns == null) {
                missingTables.add(table);
                continue;
            }
            for (String column : entry.getValue()) {
                checkedColumns++;
                if (!actualColumns.contains(column)) {
                    missingColumns.add(table + "." + column);
                }
            }
        }
        result.put("checkedTables", expected.size());
        result.put("checkedColumns", checkedColumns);
        result.put("missingTables", missingTables);
        result.put("missingColumns", missingColumns);
        if (!missingTables.isEmpty() || !missingColumns.isEmpty()) {
            result.put("status", "DRIFT");
            result.put("detail", "missingTables=" + missingTables.size() + " missingColumns=" + missingColumns.size());
            log.error("[Verify] DRIFT db='{}' missingTables={} missingColumns={}",
                    databaseName, missingTables, missingColumns);
        } else {
            result.put("status", "OK");
            result.put("detail", "tables=" + expected.size() + " columns=" + checkedColumns);
            log.warn("[Verify] OK db='{}' tables={} columns={}", databaseName, expected.size(), checkedColumns);
        }
        return result;
    }

    private Map<String, java.util.Set<String>> actualSchema(JdbcTemplate jdbcTemplate, String databaseName) {
        Map<String, java.util.Set<String>> actual = new TreeMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME AS t, COLUMN_NAME AS c FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ?",
                databaseName);
        for (Map<String, Object> row : rows) {
            String table = DataTypeUtility.stringValue(row.get("t")).toLowerCase(Locale.ROOT);
            String column = DataTypeUtility.stringValue(row.get("c")).toLowerCase(Locale.ROOT);
            if (table.isEmpty() || column.isEmpty()) {
                continue;
            }
            actual.computeIfAbsent(table, key -> new TreeSet<>()).add(column);
        }
        return actual;
    }

    /**
     * Exact runtime table/column names from Hibernate's own mapping metadata
     * (physical naming strategy already applied — no guessing).
     */
    private Map<String, java.util.Set<String>> expectedSchema() {
        Map<String, java.util.Set<String>> expected = new TreeMap<>();
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        sessionFactory.getMappingMetamodel().forEachEntityDescriptor(persister -> {
            if (!(persister instanceof AbstractEntityPersister entityPersister)) {
                return;
            }
            String table = entityPersister.getTableName().toLowerCase(Locale.ROOT);
            java.util.Set<String> columns = expected.computeIfAbsent(table, key -> new TreeSet<>());
            for (String column : entityPersister.getIdentifierColumnNames()) {
                if (column != null && !column.isBlank()) {
                    columns.add(column.toLowerCase(Locale.ROOT));
                }
            }
            int span = entityPersister.getPropertySpan();
            for (int i = 0; i < span; i++) {
                for (String column : entityPersister.getPropertyColumnNames(i)) {
                    if (column != null && !column.isBlank()) {
                        columns.add(column.toLowerCase(Locale.ROOT));
                    }
                }
            }
        });
        return expected;
    }

    private Map<String, java.util.Set<String>> expectedColumnsFor(Map<String, java.util.Set<String>> expected, boolean common) {
        Map<String, java.util.Set<String>> scoped = new TreeMap<>();
        for (Map.Entry<String, java.util.Set<String>> entry : expected.entrySet()) {
            boolean isCommonTable = COMMON_SETTINGS_TABLE.equals(entry.getKey());
            if (isCommonTable == common) {
                scoped.put(entry.getKey(), entry.getValue());
            }
        }
        return scoped;
    }

    private List<Map<String, Object>> activeTenants() {
        try {
            return companyRegistryService.listTenants(null, "ACTIVE", 100000, 0);
        } catch (Exception e) {
            log.warn("[Verify] could not list registry tenants: {}", safeMessage(e));
            return new ArrayList<>();
        }
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
}
