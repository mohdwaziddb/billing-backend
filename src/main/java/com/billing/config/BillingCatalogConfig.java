package com.billing.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class BillingCatalogConfig {

    @Value("${microservice.local.database.port:3306}")
    private String localDbPort;

    @Value("${DB_USERNAME:${microservice.local.database.username:root}}")
    private String username;

    @Value("${DB_PASSWORD:${microservice.local.database.password:root}}")
    private String password;

    @Bean(name = "billingCommonDataSource")
    public DataSource billingCommonDataSource() {
        HikariConfig config = new HikariConfig();
        // NOTE: no createDatabaseIfNotExist here on purpose. Databases are
        // created manually (sample.sql / registry docs). If billing_common
        // is missing, boot must FAIL LOUD so it gets created by hand.
        config.setJdbcUrl("jdbc:mysql://localhost:" + localDbPort + "/billing_common?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Kolkata");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(5);
        config.setPoolName("billing_common");
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(60000);
        return new HikariDataSource(config);
    }

    @Bean(name = "billingCommonJdbcTemplate")
    public JdbcTemplate billingCommonJdbcTemplate() {
        return new JdbcTemplate(billingCommonDataSource());
    }
}
