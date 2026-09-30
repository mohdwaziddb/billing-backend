package com.billing.multitenancy;

import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

@Component
public class DataSourceBasedMultiTenantConnectionProviderImpl extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl<String> {

    private static final long serialVersionUID = 1L;

    @Autowired
    @Qualifier("dataSourcesMtApp")
    private Map<String, DataSource> dataSourcesMtApp;

    @Override
    protected DataSource selectAnyDataSource() {
        if (this.dataSourcesMtApp == null || this.dataSourcesMtApp.isEmpty()) {
            throw new RuntimeException("No DataSources configured - check billing_common setup");
        }
        for (Map.Entry<String, DataSource> e : this.dataSourcesMtApp.entrySet()) {
            if (!e.getKey().endsWith("_read")) {
                return e.getValue();
            }
        }
        return this.dataSourcesMtApp.values().iterator().next();
    }

    @Override
    protected DataSource selectDataSource(String tenantIdentifier) {
        if (this.dataSourcesMtApp == null || this.dataSourcesMtApp.isEmpty()) {
            throw new RuntimeException("DataSources not initialized for tenant: " + tenantIdentifier);
        }
        DataSource ds = this.dataSourcesMtApp.get(tenantIdentifier);
        if (ds == null) {
            String base = tenantIdentifier;
            if (base != null) {
                base = base.replace("_read", "").replace("_write", "");
                ds = this.dataSourcesMtApp.get(base);
            }
            if (ds == null) {
                // Never serve a random tenant: unknown database is an explicit error
                // (tenant pools are created only for registry-listed databases).
                throw new RuntimeException("Unknown tenant database: " + tenantIdentifier
                        + ". Please contact administrator.");
            }
        }
        return ds;
    }

    public DataSourceBasedMultiTenantConnectionProviderImpl() {
        super();
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return super.getAnyConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        super.releaseAnyConnection(connection);
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        return super.getConnection(tenantIdentifier);
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        super.releaseConnection(tenantIdentifier, connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return super.supportsAggressiveRelease();
    }

    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
        return super.isUnwrappableAs(unwrapType);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return super.unwrap(unwrapType);
    }
}
