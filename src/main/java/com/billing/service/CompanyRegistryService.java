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

    public String resolveDbHostByDomain(String domain) {        if (!Registry.IS_ONLINE) {
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

    /**
     * DB host for a tenant database name (for multi-RDS live setups).
     */
    public String resolveDbHostByDatabase(String databaseName) {
        if (!Registry.IS_ONLINE) {
            return "localhost:3306";
        }
        if (databaseName == null || databaseName.trim().isEmpty()) {
            return "localhost:3306";
        }
        try {
            String host = jdbcTemplate.queryForObject(
                    "SELECT db_host FROM billing_common.company_registry WHERE database_name=? LIMIT 1",
                    String.class, databaseName.trim());
            if (host != null && !host.trim().isEmpty()) {
                return host.trim().replace("jdbc:mysql://", "").replace("/", "");
            }
        } catch (Exception e) {
            // ignore
        }
        return "localhost:3306";
    }

    public boolean isValidCompanyCode(String companyCode) {
        return resolveDbName(companyCode) != null;
    }

    /**
     * Registry gate: tenant pool/connection is created ONLY if its database is
     * registered in billing_common.company_registry (common-DB-driven tenancy).
     * Any status allowed here (admin ops need INACTIVE tenants too); request
     * routing (resolveDbName/resolveDbNameByDomain) additionally requires ACTIVE.
     */
    public boolean isValidDatabase(String databaseName) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM billing_common.company_registry WHERE database_name=?",
                    Integer.class, databaseName.trim());
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolve tenant database by company code, ignoring status.
     * Used ONLY for platform-admin /companies/{companyCode}/** routing:
     * admin ops (activate/deactivate/details) must work on INACTIVE
     * tenants too. Tenant login routing (resolveDbName/
     * resolveDbNameByDomain) keeps its own ACTIVE check.
     */
    public String resolveDbNameByCodeAnyStatus(String companyCode) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT database_name FROM billing_common.company_registry WHERE company_code=? LIMIT 1";
            try {
                return jdbcTemplate.queryForObject(sql, String.class, companyCode.trim().toUpperCase());
            } catch (Exception e) {
                return jdbcTemplate.queryForObject(sql, String.class, companyCode.trim());
            }
        } catch (Exception e) {
            return null;
        }
    }

    public java.util.List<Map<String, Object>> listTenants(String search, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT company_code, company_name, database_name, domain, db_host, status, created_at FROM billing_common.company_registry WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(company_code) LIKE ? OR LOWER(company_name) LIKE ? OR LOWER(database_name) LIKE ? OR LOWER(domain) LIKE ?)");
            String like = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status=?");
            args.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public int countTenants(String search, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM billing_common.company_registry WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(company_code) LIKE ? OR LOWER(company_name) LIKE ? OR LOWER(database_name) LIKE ? OR LOWER(domain) LIKE ?)");
            String like = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status=?");
            args.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    public Map<String, Object> overviewTenants(String search, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS total, SUM(status='ACTIVE') AS active, SUM(status<>'ACTIVE') AS inactive FROM billing_common.company_registry WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(company_code) LIKE ? OR LOWER(company_name) LIKE ? OR LOWER(database_name) LIKE ? OR LOWER(domain) LIKE ?)");
            String like = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status=?");
            args.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        try {
            return jdbcTemplate.queryForMap(sql.toString(), args.toArray());
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }
}
