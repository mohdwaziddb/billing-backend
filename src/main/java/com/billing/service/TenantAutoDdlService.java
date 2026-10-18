package com.billing.service;

import com.billing.core.Registry;
import com.billing.entity.PlatformSetting;
import com.billing.multitenancy.RequestInterceptor;
import com.billing.util.DataTypeUtility;
import jakarta.persistence.Entity;
import lombok.RequiredArgsConstructor;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.spi.DelayedDropRegistryNotAvailableImpl;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zero-touch additive schema sync: on boot, every ACTIVE tenant database gets
 * missing tables/columns created straight from the entity mappings — no
 * V*.sql file needed for additive changes. Hibernate "update" semantics:
 * only CREATEs, never ALTERs/DROPs/renames, so existing data is untouched.
 *
 * What this does NOT cover (still needs V*.sql files): data backfills,
 * renames, drops, type changes, constraint surgery, stored procedures.
 *
 * Safety: tenant scope only (billing_common untouched), per-database
 * try/catch (never fails boot), every applied change visible via
 * before/after verification diff + schema_version AUTO-DDL row.
 * Runs BEFORE TenantSchemaMigrationService in the boot chain.
 */
@Service
@RequiredArgsConstructor
public class TenantAutoDdlService {

    private static final Logger log = LoggerFactory.getLogger(TenantAutoDdlService.class);

    private static final String ENTITY_PACKAGE = "com.billing.entity";
    private static final String AUTO_DDL_VERSION = "AUTO-DDL";

    private final CompanyRegistryService companyRegistryService;
    private final RequestInterceptor requestInterceptor;
    private final TenantSchemaVerifier tenantSchemaVerifier;

    @Autowired
    @Qualifier("dataSourcesMtApp")
    private Map<String, DataSource> dataSourcesMtApp;

    /**
     * Apply additive schema updates to every ACTIVE tenant database.
     * Never throws: per-database failures are collected in the result.
     */
    public List<Map<String, Object>> applyAdditiveUpdates() {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Class<?>> entityClasses;
        try {
            entityClasses = scanEntityClasses();
            // platform_settings is a common-catalog table (billing_common only).
            // Tenant DBs must never require or auto-create it: strip it from the
            // update metadata so a drift fix cannot resurrect it in tenants.
            entityClasses.removeIf(PlatformSetting.class::equals);
        } catch (Exception e) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("scope", "ALL");
            failure.put("status", "SKIPPED");
            failure.put("detail", "Could not scan entity classes: " + safeMessage(e));
            log.error("[AutoDDL] SKIPPED: could not scan entity classes: {}", safeMessage(e));
            results.add(failure);
            return results;
        }
        if (entityClasses.isEmpty()) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("scope", "ALL");
            failure.put("status", "SKIPPED");
            failure.put("detail", "No entity classes found");
            log.error("[AutoDDL] SKIPPED: no entity classes found in {}", ENTITY_PACKAGE);
            results.add(failure);
            return results;
        }
        List<Map<String, Object>> tenants = activeTenants();
        if (tenants.isEmpty() && !Registry.IS_ONLINE) {
            String fallbackDb = Registry.dbmap.get("databasename");
            if (fallbackDb != null && !fallbackDb.isBlank()
                    && !"billing_common".equalsIgnoreCase(fallbackDb.trim())) {
                Map<String, Object> single = new LinkedHashMap<>();
                single.put("company_code", null);
                single.put("database_name", fallbackDb.trim());
                single.put("db_host", null);
                tenants = List.of(single);
            }
        }
        if (tenants.isEmpty() && Registry.IS_ONLINE) {
            log.error("[AutoDDL] registry returned ZERO active tenants — auto-DDL SKIPPED. Check billing_common.company_registry rows/status and DB privileges.");
        }
        for (Map<String, Object> tenant : tenants) {
            String code = DataTypeUtility.stringValue(tenant.get("company_code"));
            String databaseName = DataTypeUtility.stringValue(tenant.get("database_name"));
            String dbHost = DataTypeUtility.stringNullValue(tenant.get("db_host"));
            if (databaseName.isEmpty()) {
                continue;
            }
            Map<String, Object> result = applyToTenant(databaseName.trim(), dbHost, entityClasses);
            result.put("companyCode", code.isEmpty() ? null : code.toUpperCase(Locale.ROOT));
            results.add(result);
        }
        return results;
    }

    private Map<String, Object> applyToTenant(String databaseName, String dbHost, List<Class<?>> entityClasses) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseName", databaseName);
        result.put("scope", "TENANT");
        List<String> applied = new ArrayList<>();
        result.put("applied", applied);
        try {
            requestInterceptor.ensureTenant(databaseName, dbHost);
            DataSource dataSource = dataSourcesMtApp == null ? null : dataSourcesMtApp.get(databaseName);
            if (dataSource == null) {
                result.put("status", "SKIPPED");
                result.put("detail", "Tenant pool unavailable");
                log.warn("[AutoDDL] SKIPPED db='{}': tenant pool unavailable", databaseName);
                return result;
            }
            List<String> before = tenantSchemaVerifier.missingForDatabase(databaseName, dataSource);
            if (before == null) {
                result.put("status", "SKIPPED");
                result.put("detail", "Schema check unavailable");
                log.warn("[AutoDDL] SKIPPED db='{}': schema check unavailable", databaseName);
                return result;
            }
            if (before.isEmpty()) {
                result.put("status", "OK");
                result.put("detail", "already in sync");
                return result;
            }
            runUpdate(dataSource, entityClasses);
            List<String> after = tenantSchemaVerifier.missingForDatabase(databaseName, dataSource);
            List<String> fixed = new ArrayList<>(before);
            fixed.removeAll(after);
            applied.addAll(fixed);
            recordAutoDdlVersion(dataSource, fixed.size());
            if (!after.isEmpty()) {
                result.put("status", "PARTIAL");
                result.put("detail", "fixed=" + fixed.size() + " remaining=" + after.size());
                log.warn("[AutoDDL] PARTIAL db='{}' fixed={} remaining={}", databaseName, fixed, after);
            } else {
                result.put("status", "OK");
                result.put("detail", "applied=" + fixed.size());
                log.warn("[AutoDDL] OK db='{}' applied={}", databaseName, fixed);
            }
            result.put("remaining", after);
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("detail", safeMessage(e));
            log.warn("[AutoDDL] FAILED db='{}': {}", databaseName, safeMessage(e));
        }
        return result;
    }

    private void runUpdate(DataSource dataSource, List<Class<?>> entityClasses) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DATASOURCE, dataSource);
        settings.put(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect");
        settings.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        settings.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY,
                "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        settings.put(AvailableSettings.HBM2DDL_AUTO, Action.UPDATE);
        settings.put(AvailableSettings.HBM2DDL_HALT_ON_ERROR, Boolean.FALSE);
        StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            Metadata metadata = new MetadataSources(serviceRegistry)
                    .addAnnotatedClasses(entityClasses.toArray(new Class<?>[0]))
                    .buildMetadata();
            Map<String, Object> configuration = new HashMap<>();
            configuration.put(AvailableSettings.HBM2DDL_AUTO, Action.UPDATE);
            configuration.put(AvailableSettings.HBM2DDL_HALT_ON_ERROR, Boolean.FALSE);
            SchemaManagementToolCoordinator.process(
                    metadata,
                    serviceRegistry,
                    configuration,
                    DelayedDropRegistryNotAvailableImpl.INSTANCE);
        } finally {
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
        }
    }

    private List<Class<?>> scanEntityClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> classes = new ArrayList<>();
        for (org.springframework.beans.factory.config.BeanDefinition candidate
                : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
            String className = candidate.getBeanClassName();
            if (className != null && !className.isBlank()) {
                classes.add(Class.forName(className));
            }
        }
        classes.sort(java.util.Comparator.comparing(Class::getName));
        return classes;
    }

    private void recordAutoDdlVersion(DataSource dataSource, int appliedCount) {
        if (appliedCount <= 0) {
            return;
        }
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.setQueryTimeout(60);
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version VARCHAR(255) NOT NULL, "
                    + "description VARCHAR(500) NULL, "
                    + "applied_at DATETIME(6) NOT NULL, "
                    + "success BIT(1) NOT NULL DEFAULT b'1', "
                    + "PRIMARY KEY (version)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            jdbcTemplate.update("INSERT INTO schema_version (version, description, applied_at, success)"
                            + " VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
                            + " description = VALUES(description), applied_at = VALUES(applied_at), success = VALUES(success)",
                    AUTO_DDL_VERSION, "additive auto-DDL applied=" + appliedCount,
                    LocalDateTime.now(), true);
        } catch (Exception e) {
            log.warn("[AutoDDL] could not record version row: {}", safeMessage(e));
        }
    }

    private List<Map<String, Object>> activeTenants() {
        try {
            return companyRegistryService.listTenants(null, "ACTIVE", 100000, 0);
        } catch (Exception e) {
            log.warn("[AutoDDL] could not list registry tenants: {}", safeMessage(e));
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
