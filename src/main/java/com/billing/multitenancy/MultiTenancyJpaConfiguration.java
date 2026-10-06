package com.billing.multitenancy;

import com.billing.core.CompanyDomainInfo;
import com.billing.core.Registry;
import com.billing.core.appconfig.ApplicationConstant;
import com.billing.platform.tomcat.MysqlDataSourceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.*;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@ComponentScan("com.billing")
@Lazy
public class MultiTenancyJpaConfiguration {

    @Autowired
    private JpaProperties jpaProperties;

    @Value("${microservice.local.database.port:3306}")
    private String localDbPort;

    private HikariDataSource setDynamicDataSource(String jdbcUrl, String username, String password, String driverClassName, String tenant, boolean isRead) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(20);
        config.setIdleTimeout(60000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(900000);
        config.setInitializationFailTimeout(10000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        String poolName = tenant + (isRead ? "-READ" : "-WRITE");
        config.setPoolName(poolName);
        if (isRead && com.billing.core.Registry.IS_ONLINE) {
            config.setReadOnly(true);
        }
        HikariDataSource dataSource = new HikariDataSource(config);
        return dataSource;
    }

    @Primary
    @Bean(name = "dataSourcesMtApp")
    public Map<String, DataSource> dataSourcesMtApp(
            @Qualifier("billingCommonJdbcTemplate") JdbcTemplate billingCommonJdbcTemplate) {
        Map<String, DataSource> result = new HashMap<>();
        // Env-aware credentials (DB_USERNAME/DB_PASSWORD, else root defaults)
        // for BOTH modes: the ONLINE branch below reads Registry.dbmap too,
        // and nothing else populates it there.
        MysqlDataSourceService.loadDatabaseCredentialsFromLocalHost();
        String url = "jdbc:mysql://localhost:" + localDbPort + "/";
        String readerUrl = "jdbc:mysql://localhost:" + localDbPort + "/";
        String username = "";
        String password = "";
        String driverClassName = "com.mysql.cj.jdbc.Driver";
        if (Registry.IS_ONLINE) {
            String envUrl = Registry.dbmap.get("url");
            String envReader = Registry.dbmap.get("urlreader");
            String envUser = Registry.dbmap.get("username");
            String envPass = Registry.dbmap.get("password");
            if (envUrl != null) {
                url = envUrl.contains("jdbc:mysql://") ? envUrl : "jdbc:mysql://" + envUrl + "/";
                if (!url.endsWith("/")) url = url + "/";
            }
            if (envReader != null) {
                readerUrl = envReader.contains("jdbc:mysql://") ? envReader : "jdbc:mysql://" + envReader + "/";
                if (!readerUrl.endsWith("/")) readerUrl = readerUrl + "/";
            } else {
                readerUrl = url;
            }
            if (envUser != null) username = envUser;
            if (envPass != null) password = envPass;
        } else {
            username = Registry.dbmap.get("username");
            password = Registry.dbmap.get("password");
            String dbUrl = Registry.dbmap.get("url");
            if (dbUrl != null && !dbUrl.isBlank()) {
                url = "jdbc:mysql://" + dbUrl.replace("jdbc:mysql://", "").replace("/", "") + "/";
                readerUrl = url;
            }
        }

        HashSet<String> dbSet = new HashSet<>();
        if (Registry.IS_ONLINE) {
            for (String tenantDomain : CompanyDomainInfo.domainInfoJSON.keySet()) {
                try {
                    JSONObject tenantJson = CompanyDomainInfo.domainInfoJSON.get(tenantDomain);
                    if (tenantJson != null && tenantJson.length() > 0) {
                        String tenant = tenantJson.optString("database");
                        if (dbSet.contains(tenant)) {
                            continue;
                        }
                        dbSet.add(tenant);
                        String databaseIp = tenantJson.optString("databaseip");
                        if (databaseIp == null || databaseIp.isBlank()) {
                            databaseIp = url;
                        }
                        String hostPort = databaseIp.replace("jdbc:mysql://", "").replace("/", "").trim();
                        String jdbcBase = "jdbc:mysql://" + hostPort + "/";
                        if (url.equalsIgnoreCase(jdbcBase) || url.contains(hostPort) || hostPort.contains("localhost")) {
                            HikariDataSource dsWrite = setDynamicDataSource(jdbcBase + tenant, username, password, driverClassName, tenant, false);
                            result.put(tenant, dsWrite);
                            HikariDataSource dsRead = setDynamicDataSource(jdbcBase + tenant, username, password, driverClassName, tenant, true);
                            result.put(tenant + ApplicationConstant.CONNECTION_READ_STRING, dsRead);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Registry-driven tenants: every database registered in
            // billing_common.company_registry gets WRITE+READ pools at startup,
            // otherwise the map stays empty and the first JPA access fails with
            // "DataSources not initialized for tenant" and kills the boot.
            // (ACTIVE or INACTIVE alike; request routing still honors status.)
            addRegistryTenants(result, dbSet, billingCommonJdbcTemplate, url, readerUrl, username, password, driverClassName);
        } else {
            // local: create for each unique tenant in domainInfoJSON
            for (String key : CompanyDomainInfo.domainInfoJSON.keySet()) {
                try {
                    JSONObject json = CompanyDomainInfo.domainInfoJSON.get(key);
                    String tenant = json.optString("database");
                    if (tenant == null || tenant.isBlank() || dbSet.contains(tenant)) {
                        continue;
                    }
                    dbSet.add(tenant);
                    HikariDataSource dsWrite = setDynamicDataSource(url + tenant, username, password, driverClassName, tenant, false);
                    result.put(tenant, dsWrite);
                    HikariDataSource dsRead = setDynamicDataSource(readerUrl + tenant, username, password, driverClassName, tenant, true);
                    result.put(tenant + ApplicationConstant.CONNECTION_READ_STRING, dsRead);
                } catch (Exception e) {
                    System.err.println("[ERROR] Failed to create DataSource for tenant '" + CompanyDomainInfo.domainInfoJSON.get(key).optString("database") + "': " + e.getMessage());
                    System.err.println("[HINT] Ensure MySQL is running and the configured database exists (Registry.dbmap databasename, set in MysqlDataSourceService).");
                    throw new RuntimeException("Failed to initialize datasource for tenant '" + CompanyDomainInfo.domainInfoJSON.get(key).optString("database") + "'", e);
                }
            }
            // fallback if map still empty (e.g., fresh install with only maacreation)
        // Registry-driven tenants: every database registered in
        // billing_common.company_registry gets WRITE+READ pools at startup,
        // so startup runners (backfills) and requests never hit
        // "Unknown tenant database" for tenants missing from the static map.
        addRegistryTenants(result, dbSet, billingCommonJdbcTemplate, url, readerUrl, username, password, driverClassName);

        if (result.isEmpty()) {
                String tenant = Registry.dbmap.get("databasename");
                if (tenant == null || tenant.isBlank()) {
                    throw new RuntimeException("Unable to resolve default company database. Check local DB config (Registry.dbmap databasename).");
                }
                try {
                    HikariDataSource dsWrite = setDynamicDataSource(url + tenant, username, password, driverClassName, tenant, false);
                    result.put(tenant, dsWrite);
                    HikariDataSource dsRead = setDynamicDataSource(readerUrl + tenant, username, password, driverClassName, tenant, true);
                    result.put(tenant + ApplicationConstant.CONNECTION_READ_STRING, dsRead);
                } catch (Exception e) {
                    System.err.println("[ERROR] Failed to create DataSource for fallback tenant '" + tenant + "': " + e.getMessage());
                    throw new RuntimeException("Failed to initialize datasource for fallback tenant '" + tenant + "'", e);
                }
            }
        }

        if (result.isEmpty()) {
            System.err.println("[WARN] No tenants resolved - dataSourcesMtApp is empty! Check CompanyDomainInfo or local DB config.");
        } else {
            System.out.println("[INFO] Initialized DataSources for tenants: " + result.keySet());
        }
        return result;
    }

    private void addRegistryTenants(Map<String, DataSource> result, HashSet<String> dbSet,
            JdbcTemplate billingCommonJdbcTemplate, String url, String readerUrl,
            String username, String password, String driverClassName) {
        List<Map<String, Object>> rows;
        try {
            rows = billingCommonJdbcTemplate.queryForList(
                    "SELECT database_name, db_host FROM billing_common.company_registry");
        } catch (Exception e) {
            System.err.println("[WARN] Could not read billing_common.company_registry, skipping registry-driven tenant init: " + e.getMessage());
            return;
        }
        for (Map<String, Object> row : rows) {
            Object dbRaw = row.get("database_name");
            if (dbRaw == null) {
                continue;
            }
            String tenant = dbRaw.toString().trim();
            if (tenant.isEmpty() || dbSet.contains(tenant)) {
                continue;
            }
            dbSet.add(tenant);
            String jdbcBase = url;
            String readerBase = readerUrl;
            Object hostRaw = row.get("db_host");
            if (hostRaw != null && !hostRaw.toString().isBlank()) {
                String hostPort = hostRaw.toString().trim().replace("jdbc:mysql://", "").replace("/", "");
                if (!hostPort.isEmpty()) {
                    jdbcBase = "jdbc:mysql://" + hostPort + "/";
                    readerBase = jdbcBase;
                }
            }
            try {
                HikariDataSource dsWrite = setDynamicDataSource(jdbcBase + tenant, username, password, driverClassName, tenant, false);
                result.put(tenant, dsWrite);
                HikariDataSource dsRead = setDynamicDataSource(readerBase + tenant, username, password, driverClassName, tenant, true);
                result.put(tenant + ApplicationConstant.CONNECTION_READ_STRING, dsRead);
                System.out.println("[INFO] Initialized registry-driven DataSources for tenant: " + tenant);
            } catch (Exception e) {
                System.err.println("[WARN] Failed to create DataSource for registry tenant '" + tenant + "': " + e.getMessage());
            }
        }
    }

    @Bean
    public AbstractDataSourceBasedMultiTenantConnectionProviderImpl multiTenantConnectionProvider() {
        return new DataSourceBasedMultiTenantConnectionProviderImpl();
    }

    @Bean
    public CurrentTenantIdentifierResolver currentTenantIdentifierResolver() {
        return new CurrentTenantIdentifierResolverImpl();
    }

    @Bean(name = "entityManagerFactoryBean")
    public LocalContainerEntityManagerFactoryBean entityManagerFactoryBean(
            AbstractDataSourceBasedMultiTenantConnectionProviderImpl multiTenantConnectionProvider,
            CurrentTenantIdentifierResolver currentTenantIdentifierResolver) {

        Map<String, Object> hibernateProps = new LinkedHashMap<>();
        hibernateProps.putAll(this.jpaProperties.getProperties());
        hibernateProps.put("hibernate.multiTenancy", "DATABASE");
        hibernateProps.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, multiTenantConnectionProvider);
        hibernateProps.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, currentTenantIdentifierResolver);
        hibernateProps.put("hibernate.hbm2ddl.auto", Registry.IS_ONLINE ? "none" : "update");
        hibernateProps.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        hibernateProps.put("hibernate.enable_lazy_load_no_trans", "true");
        hibernateProps.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        hibernateProps.put("hibernate.implicit_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");

        LocalContainerEntityManagerFactoryBean result = new LocalContainerEntityManagerFactoryBean();
        result.setPackagesToScan("com.billing");
        result.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        result.setJpaPropertyMap(hibernateProps);
        result.setPersistenceUnitName("billingEntityManager");
        return result;
    }

    @Bean
    @Primary
    public EntityManagerFactory entityManagerFactory(LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
        return entityManagerFactoryBean.getObject();
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
