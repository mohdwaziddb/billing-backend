package com.billing.config;

import com.billing.service.PlatformAdminService;
import com.billing.service.TenantSchemaMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Deploy-time self-heal: on every backend boot, bring every ACTIVE tenant
 * database in sync with the deployed code — schema migrations first (new
 * tables/columns must exist before seeds), then seed sweep (new
 * menus/permissions). billing_common migrations run once before tenants.
 *
 * Local single-DB dev: the tenant loop finds no registry rows and skips the
 * seed sweep (the boot PermissionDataInitializer already covers that one
 * database), but file migrations still apply to it.
 *
 * Never fails startup: every tenant/file is individually guarded and only
 * logged. Disable via properties when needed:
 * app.migration.enabled / app.seed-sweep.enabled (default true).
 */
@Component
@RequiredArgsConstructor
public class TenantSeedSweepRunner implements ApplicationRunner {

    private final TenantSchemaMigrationService tenantSchemaMigrationService;
    private final PlatformAdminService platformAdminService;

    @Value("${app.migration.enabled:true}")
    private boolean migrationEnabled;

    @Value("${app.seed-sweep.enabled:true}")
    private boolean seedSweepEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (migrationEnabled) {
            try {
                List<Map<String, Object>> migrations = tenantSchemaMigrationService.migrateAll();
                long failed = migrations.stream()
                        .filter(result -> "FAILED".equals(result.get("status")))
                        .count();
                System.out.println("[INFO] Boot migration sweep done: databases=" + migrations.size()
                        + " failed=" + failed);
            } catch (Exception e) {
                System.err.println("[WARN] Boot migration sweep skipped: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        } else {
            System.out.println("[INFO] Boot migration sweep disabled (app.migration.enabled=false).");
        }
        if (seedSweepEnabled) {
            try {
                List<Map<String, Object>> seeds = platformAdminService.sweepSeedsForAllActiveTenants();
                long failed = seeds.stream()
                        .filter(result -> "FAILED".equals(result.get("status")))
                        .count();
                System.out.println("[INFO] Boot seed sweep done: tenants=" + seeds.size()
                        + " failed=" + failed);
            } catch (Exception e) {
                System.err.println("[WARN] Boot seed sweep skipped: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        } else {
            System.out.println("[INFO] Boot seed sweep disabled (app.seed-sweep.enabled=false).");
        }
    }
}
