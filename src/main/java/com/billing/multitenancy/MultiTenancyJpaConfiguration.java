package com.billing.multitenancy;

import com.billing.core.Registry;
import com.billing.core.appconfig.ApplicationConstant;
import com.billing.platform.tomcat.MysqlDataSourceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
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
import java.util.LinkedHashMap;
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

    // Single source: spring.datasource.* (local properties / prod env).
    @Value("${spring.datasource.url:}")
    private String primaryUrl;

    @Value("${spring.datasource.username:}")
    private String springUsername;

    @Value("${spring.datasource.password:}")
    private String springPassword;

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
        // Concurrent: lazy tenant pools request threads se bante hain.
        Map<String, DataSource> result = new java.util.concurrent.ConcurrentHashMap<>();
        // Single source: Spring spring.datasource.* ko Registry me prime karo,
        // phir env override (prod) loadDatabaseCredentials me lagta hai.
        // billing_common + tenant same server, isliye host:port primary URL se.
        if (primaryUrl != null && !primaryUrl.isBlank()) {
            String hostPort = extractHostPort(primaryUrl);
            Registry.dbmap.putIfAbsent("url", hostPort);
            Registry.dbmap.putIfAbsent("urlreader", hostPort);
        }
        if (springUsername != null && !springUsername.isBlank()) {
            Registry.dbmap.putIfAbsent("username", springUsername);
        }
        if (springPassword != null && !springPassword.isBlank()) {
            Registry.dbmap.putIfAbsent("password", springPassword);
        }
        MysqlDataSourceService.loadDatabaseCredentialsFromLocalHost();
        String springBase = (primaryUrl != null && !primaryUrl.isBlank())
                ? "jdbc:mysql://" + extractHostPort(primaryUrl) + "/"
                : "jdbc:mysql://localhost:" + localDbPort + "/";
        String url = springBase;
        String readerUrl = springBase;
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

        // Lazy model (500-tenant ready): boot par sirf 1 bootstrap pool.
        // Default tenant first (local me `sample`; runners/scheduler/resolver +
        // Hibernate any-connection sab isi ko mangte hain), missing hua to pehli
        // ACTIVE registry row. Baaki tenant pools pehli request par
        // RequestInterceptor.ensureTenantDataSource se bante hain.
        java.util.List<String> candidates = new java.util.ArrayList<>();
        String fallbackTenant = Registry.dbmap.get("databasename");
        if (fallbackTenant != null && !fallbackTenant.isBlank()) {
            candidates.add(fallbackTenant.trim());
        }
        for (String activeDb : activeRegistryDatabases(billingCommonJdbcTemplate)) {
            if (!candidates.contains(activeDb)) {
                candidates.add(activeDb);
            }
        }
        String lastFailure = null;
        for (String tenant : candidates) {
            if (tenant == null || tenant.isBlank() || result.containsKey(tenant)) {
                continue;
            }
            String jdbcBase = resolveRegistryBase(billingCommonJdbcTemplate, tenant, url);
            String readerBase = resolveRegistryBase(billingCommonJdbcTemplate, tenant, readerUrl);
            try {
                HikariDataSource dsWrite = setDynamicDataSource(jdbcBase + tenant, username, password, driverClassName, tenant, false);
                result.put(tenant, dsWrite);
                HikariDataSource dsRead = setDynamicDataSource(readerBase + tenant, username, password, driverClassName, tenant, true);
                result.put(tenant + ApplicationConstant.CONNECTION_READ_STRING, dsRead);
                System.out.println("[INFO] Bootstrap tenant pool ready: " + tenant + " (rest lazy on first request)");
                lastFailure = null;
                break;
            } catch (Exception e) {
                lastFailure = tenant + ": " + e.getMessage();
                System.err.println("[WARN] Skipping bootstrap candidate '" + tenant + "': " + e.getMessage());
            }
        }
        if (result.isEmpty()) {
            throw new RuntimeException("No tenant pool available at boot"
                    + (lastFailure == null
                            ? " (billing_common.company_registry empty and default database missing?)"
                            : ". Last error -> " + lastFailure));
        }

        System.out.println("[INFO] Initialized DataSources for tenants: " + result.keySet());
        return result;
    }

    private static String extractHostPort(String jdbcUrl) {
        String tmp = jdbcUrl.replace("jdbc:mysql://", "");
        int slash = tmp.indexOf('/');
        if (slash > 0) {
            tmp = tmp.substring(0, slash);
        }
        tmp = tmp.trim();
        if (tmp.isEmpty()) {
            return "localhost:3306";
        }
        return tmp;
    }

    private static java.util.List<String> activeRegistryDatabases(JdbcTemplate billingCommonJdbcTemplate) {
        try {
            java.util.List<Map<String, Object>> rows = billingCommonJdbcTemplate.queryForList(
                    "SELECT database_name FROM billing_common.company_registry WHERE status='ACTIVE'");
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object dbRaw = row.get("database_name");
                if (dbRaw != null && !dbRaw.toString().isBlank()) {
                    out.add(dbRaw.toString().trim());
                }
            }
            return out;
        } catch (Exception e) {
            System.err.println("[WARN] Could not read billing_common.company_registry for bootstrap: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private static String resolveRegistryBase(JdbcTemplate billingCommonJdbcTemplate, String tenant, String fallbackBase) {
        try {
            String host = billingCommonJdbcTemplate.queryForObject(
                    "SELECT db_host FROM billing_common.company_registry WHERE database_name=? LIMIT 1",
                    String.class, tenant);
            if (host != null && !host.isBlank()) {
                String hostPort = host.trim().replace("jdbc:mysql://", "").replace("/", "");
                if (!hostPort.isEmpty()) {
                    return "jdbc:mysql://" + hostPort + "/";
                }
            }
        } catch (Exception ignored) {
        }
        return fallbackBase;
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
