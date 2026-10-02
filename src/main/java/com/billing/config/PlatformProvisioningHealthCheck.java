package com.billing.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Never-again guard: on every boot, cross-check billing_common.company_registry
 * against actually existing databases and log orphans loudly instead of
 * letting half-provisioned state hide silently.
 * Read-only: never creates, drops, or modifies anything.
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class PlatformProvisioningHealthCheck implements ApplicationRunner {

    @Qualifier("billingCommonJdbcTemplate")
    private final JdbcTemplate billingCommonJdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Map<String, Object>> rows = billingCommonJdbcTemplate.queryForList(
                    "SELECT company_code, database_name, status FROM billing_common.company_registry");
            if (rows.isEmpty()) {
                System.out.println("[INFO] Provisioning check: company_registry is empty, no tenants to verify.");
                return;
            }
            Set<String> existing = new HashSet<>();
            try {
                List<Map<String, Object>> schemas = billingCommonJdbcTemplate.queryForList(
                        "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA");
                for (Map<String, Object> schema : schemas) {
                    Object name = schema.get("SCHEMA_NAME");
                    if (name != null) {
                        existing.add(name.toString());
                    }
                }
            } catch (Exception e) {
                System.err.println("[WARN] Provisioning check: could not list databases: " + e.getMessage());
                return;
            }
            for (Map<String, Object> row : rows) {
                Object codeRaw = row.get("company_code");
                Object dbRaw = row.get("database_name");
                Object statusRaw = row.get("status");
                String code = codeRaw == null ? "?" : codeRaw.toString();
                String db = dbRaw == null ? "" : dbRaw.toString();
                if (db.isEmpty() || !existing.contains(db)) {
                    System.err.println("[WARN] Provisioning check: ORPHAN registry row, database missing -> code="
                            + code + " database=" + db + " status=" + statusRaw);
                    continue;
                }
                System.out.println("[INFO] Provisioning check: OK code=" + code + " database=" + db
                        + " status=" + statusRaw);
            }
        } catch (Exception e) {
            // Health check must never fail startup.
            System.err.println("[WARN] Provisioning check skipped: " + e.getMessage());
        }
    }
}
