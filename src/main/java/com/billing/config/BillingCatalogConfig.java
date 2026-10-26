package com.billing.config;

import com.billing.core.Registry;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class BillingCatalogConfig {

    // Single source: spring.datasource.* (local -> application-local.properties,
    // prod -> DB_URL / DB_USERNAME / DB_PASSWORD via application-prod.properties).
    // billing_common same server par hai, isliye host:port primary URL se nikalo.
    // Java me koi password literal nahi — hrhk sirf application-local.properties me.
    @Value("${spring.datasource.url}")
    private String primaryUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean(name = "billingCommonDataSource")
    public DataSource billingCommonDataSource() {
        HikariConfig config = new HikariConfig();
        // NOTE: no createDatabaseIfNotExist here on purpose. Databases are
        // created manually (sample.sql / registry docs). If billing_common
        // is missing, boot must FAIL LOUD so it gets created by hand.
        config.setJdbcUrl("jdbc:mysql://" + Registry.extractHostPort(primaryUrl) + "/billing_common?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Kolkata");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // Registry lookups (RequestInterceptor) isi pool se: ek connection warm rakho.
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(5);
        config.setPoolName("billing_common");
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);
        return new HikariDataSource(config);
    }

    @Bean(name = "billingCommonJdbcTemplate")
    public JdbcTemplate billingCommonJdbcTemplate(
            @Qualifier("billingCommonDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
