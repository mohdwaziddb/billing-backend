package com.billing.service;

import com.billing.core.Registry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CompanyRegistryService {

    @Autowired
    @Qualifier("billingCommonJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    public String resolveDbName(String companyCode) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT database_name FROM billing_common.company_registry WHERE company_code=? AND status='ACTIVE' LIMIT 1";
            try {
                return jdbcTemplate.queryForObject(sql, String.class, companyCode.trim().toUpperCase());
            } catch (Exception e) {
                return jdbcTemplate.queryForObject(sql, String.class, companyCode.trim());
            }
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> getCompanyInfo(String companyCode) {
        try {
            String sql = "SELECT company_code, company_name, database_name, db_host, status, domain FROM billing_common.company_registry WHERE company_code=? LIMIT 1";
            try {
                return jdbcTemplate.queryForMap(sql, companyCode.trim().toUpperCase());
            } catch (Exception ex) {
                return jdbcTemplate.queryForMap(sql, companyCode.trim());
            }
        } catch (Exception e) {
            return null;
        }
    }

    public String resolveDbHost(String companyCode) {
        if (!Registry.IS_ONLINE) {
            return "localhost:3306";
        }
        Map<String, Object> info = getCompanyInfo(companyCode);
        if (info != null && info.get("db_host") != null) {
            String host = info.get("db_host").toString().trim();
            if (!host.isEmpty()) {
                return host.replace("jdbc:mysql://", "").replace("/", "");
            }
        }
        return "localhost:3306";
    }

    public String resolveDbNameByDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT database_name FROM billing_common.company_registry WHERE domain=? AND status='ACTIVE' LIMIT 1";
            try {
                return jdbcTemplate.queryForObject(sql, String.class, domain.trim());
            } catch (Exception e) {
                String domainWithoutPort = domain.split(":")[0];
                return jdbcTemplate.queryForObject(sql, String.class, domainWithoutPort);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public String resolveDbHostByDomain(String domain) {
        if (!Registry.IS_ONLINE) {
            return "localhost:3306";
        }
        try {
            String sql = "SELECT db_host FROM billing_common.company_registry WHERE domain=? LIMIT 1";
            String host = null;
            try {
                host = jdbcTemplate.queryForObject(sql, String.class, domain.trim());
            } catch (Exception e) {
                String domainWithoutPort = domain.split(":")[0];
                host = jdbcTemplate.queryForObject(sql, String.class, domainWithoutPort);
            }
            if (host != null && !host.isEmpty()) {
                return host.replace("jdbc:mysql://", "").replace("/", "");
            }
        } catch (Exception e) {
            // ignore
        }
        return "localhost:3306";
    }

    public boolean isValidCompanyCode(String companyCode) {
        return resolveDbName(companyCode) != null;
    }
}
