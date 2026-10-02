package com.billing.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContextHolder.getTenant();
        if (tenant != null && !tenant.isBlank()) {
            return tenant;
        }
        // Single source of truth: the DB name configured in MysqlDataSourceService
        // (Registry.dbmap "databasename"). No hardcoded tenant name anywhere.
        String fallback = com.billing.core.Registry.dbmap.get("databasename");
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException(
                    "Default tenant database is not configured (Registry.dbmap databasename). Set it in MysqlDataSourceService.");
        }
        return fallback;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
