package com.billing.service;

import com.billing.config.PermissionDataInitializer;
import com.billing.dto.PageResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyDetailsResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyOverviewResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyResponse;
import com.billing.dto.platformadmin.PlatformAdminDashboardResponse;
import com.billing.dto.platformadmin.PlatformAdminSettingsRequest;
import com.billing.dto.platformadmin.PlatformAdminSettingsResponse;
import com.billing.dto.platformadmin.PlatformAdminUserResponse;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.multitenancy.RequestInterceptor;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.AuditLogRepository;
import com.billing.repository.CompanyRepository;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.util.DataTypeUtility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminService.class);

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionDataInitializer permissionDataInitializer;
    private final TaxMasterService taxMasterService;
    private final PaymentModeMasterService paymentModeMasterService;
    private final CompanyRegistryService companyRegistryService;
    private final RequestInterceptor requestInterceptor;
    private final TenantSchemaMigrationService tenantSchemaMigrationService;
    private final TenantSchemaVerifier tenantSchemaVerifier;
    private final TenantAutoDdlService tenantAutoDdlService;
    @Qualifier("billingCommonJdbcTemplate")
    private final JdbcTemplate billingCommonJdbcTemplate;

    @Transactional(readOnly = true)
    public PlatformAdminDashboardResponse dashboard() {
        // All DBs visible: counts come from the common catalog (company_registry), not one tenant.
        Map<String, Object> overview = companyRegistryService.overviewTenants(null, null);
        long total = toLong(overview.get("total"));
        long active = toLong(overview.get("active"));
        return PlatformAdminDashboardResponse.builder()
                .totalCompanies((int) total)
                .activeCompanies(active)
                .inactiveCompanies(Math.max(0, total - active))
                .build();
    }

    // NOT transactional on purpose (NEVER-AGAIN RULE, see note below the class):
    // each row's tenant switch + JPA read must open its own tx via the self
    // proxy inside withTenantDb, otherwise the connection binds to the entry
    // tenant and only that tenant's company enriches correctly.
    public PageResponse<PlatformAdminCompanyResponse> companies(int page, int size, String search, Boolean active) {
        int resolvedPage = Math.max(0, page);
        int resolvedSize = Math.max(1, Math.min(size, 100));
        String status = active == null ? null : (active ? "ACTIVE" : "INACTIVE");
        int total = companyRegistryService.countTenants(normalizeSearch(search), status);
        List<Map<String, Object>> rows = companyRegistryService.listTenants(
                normalizeSearch(search), status, resolvedSize, resolvedPage * resolvedSize);
        List<PlatformAdminCompanyResponse> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            records.add(enrichTenantRow(row));
        }
        int totalPages = resolvedSize == 0 ? 0 : (int) Math.ceil(total / (double) resolvedSize);
        return PageResponse.<PlatformAdminCompanyResponse>builder()
                .records(records)
                .page(resolvedPage)
                .size(resolvedSize)
                .totalRecords(total)
                .totalPages(totalPages)
                .build();
    }

    public PageResponse<PlatformAdminCompanyResponse> companies(Map<String, Object> param) {
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        Boolean active = null;
        Object activeRaw = param.get("active");
        if (activeRaw != null) {
            String activeStr = DataTypeUtility.stringValue(activeRaw);
            if (activeStr.length() > 0) {
                active = DataTypeUtility.booleanValue(activeRaw);
            }
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return companies(page, size, search, active);
    }

    public PlatformAdminCompanyOverviewResponse companyOverview(String search, Boolean active) {
        String status = active == null ? null : (active ? "ACTIVE" : "INACTIVE");
        List<Map<String, Object>> rows = companyRegistryService.listTenants(normalizeSearch(search), status, 100000, 0);
        long owners = 0;
        long admins = 0;
        long users = 0;
        for (Map<String, Object> row : rows) {
            long[] counts = countTenantUsers(row);
            owners += counts[0];
            admins += counts[1];
            users += counts[2];
        }
        return PlatformAdminCompanyOverviewResponse.builder()
                .companyCount(rows.size())
                .ownerCount(owners)
                .adminCount(admins)
                .userCount(users)
                .build();
    }

    public PlatformAdminCompanyOverviewResponse companyOverview(Map<String, Object> param) {
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        Boolean active = null;
        Object activeRaw = param.get("active");
        if (activeRaw != null) {
            String activeStr = DataTypeUtility.stringValue(activeRaw);
            if (activeStr.length() > 0) {
                active = DataTypeUtility.booleanValue(activeRaw);
            }
        }
        return companyOverview(search, active);
    }

    /**
     * NEVER-AGAIN RULE: a JPA transaction must start only AFTER the tenant is
     * switched. @Transactional on an outer method begins the Tx at entry,
     * binding the connection to the entry-time tenant; the switch inside
     * would come too late and writes would land in the wrong database.
     * So outer methods below are NOT transactional: they resolve the DB,
     * switch tenant via withTenantDb/withCompanyCodeDb, then delegate to an
     * inner @Transactional method through the self proxy.
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private PlatformAdminService self;

    public PlatformAdminCompanyResponse repairCompanySeeds(String companyCode) {
        return withCompanyCodeDb(companyCode, () -> self.repairCompanySeedsData(companyCode));
    }

    @Transactional
    public PlatformAdminCompanyResponse repairCompanySeedsData(String companyCode) {
        Company company = requireCompany(companyCode);
        permissionDataInitializer.seedBaseMasters();
        permissionDataInitializer.seedPermissionsForCompany(company);
        permissionDataInitializer.seedThemeForCompany(company);
        permissionDataInitializer.seedDefaultNotificationChannels(company);
        taxMasterService.createDefaultTaxesForCompany(company);
        paymentModeMasterService.ensureDefaults(company);
        verifyProvisionedTenant(company);
        return toCompanyResponse(company);
    }

    /**
     * Deploy-time sweep: re-runs seed repair for every ACTIVE tenant so new
     * menus/permissions from MasterSeedCatalog reach live databases without
     * per-company manual calls. Per-tenant failures are collected, never thrown.
     */
    public List<Map<String, Object>> sweepSeedsForAllActiveTenants() {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> tenants = new ArrayList<>();
        try {
            tenants = companyRegistryService.listTenants(null, "ACTIVE", 100000, 0);
        } catch (Exception e) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("status", "FAILED");
            failure.put("detail", "Could not list registry tenants");
            results.add(failure);
            return results;
        }
        for (Map<String, Object> tenant : tenants) {
            String code = tenant.get("company_code") == null ? "" : tenant.get("company_code").toString().trim();
            String databaseName = tenant.get("database_name") == null ? "" : tenant.get("database_name").toString().trim();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("companyCode", code.isEmpty() ? null : code.toUpperCase(Locale.ROOT));
            result.put("databaseName", databaseName.isEmpty() ? null : databaseName);
            if (code.isEmpty()) {
                result.put("status", "SKIPPED");
                result.put("detail", "Missing company_code");
                results.add(result);
                continue;
            }
            try {
                long before = countActiveMenus(code);
                withCompanyCodeDb(code, () -> self.repairCompanySeedsData(code));
                long after = countActiveMenus(code);
                result.put("status", "OK");
                result.put("detail", "menusBefore=" + before + " menusAfter=" + after);
            } catch (Exception e) {
                result.put("status", "FAILED");
                result.put("detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    /**
     * One-call full sync (bulk backup for post-deploy): additive auto-DDL
     * first (new tables/columns without files), then schema migrations,
     * then seed sweep, then schema verification proving entity/DB match.
     */
    public Map<String, Object> repairAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> autoDdl = new ArrayList<>();
        try {
            autoDdl = tenantAutoDdlService.applyAdditiveUpdates();
        } catch (Exception e) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("status", "FAILED");
            failure.put("detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            autoDdl.add(failure);
        }
        result.put("autoDdl", autoDdl);
        List<Map<String, Object>> migrations = new ArrayList<>();
        try {
            migrations = tenantSchemaMigrationService.migrateAll();
        } catch (Exception e) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("status", "FAILED");
            failure.put("detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            migrations.add(failure);
        }
        result.put("migrations", migrations);
        result.put("seeds", sweepSeedsForAllActiveTenants());
        List<Map<String, Object>> verification = new ArrayList<>();
        try {
            verification = tenantSchemaVerifier.verifyAll();
        } catch (Exception e) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("status", "SKIPPED");
            failure.put("detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            verification.add(failure);
        }
        result.put("verification", verification);
        return result;
    }

    private long countActiveMenus(String companyCode) {
        try {
            return withCompanyCodeDb(companyCode, () -> self.countActiveMenusData());
        } catch (Exception ignored) {
            return -1;
        }
    }

    @Transactional(readOnly = true)
    public long countActiveMenusData() {
        try {
            return permissionDataInitializer.countActiveMenus();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void verifyProvisionedTenant(Company company) {
        List<String> problems = new ArrayList<>();
        try {
            if (companyRepository.count() != 1) {
                problems.add("companies!=" + companyRepository.count());
            }
        } catch (Exception e) {
            problems.add("companies-unreadable");
        }
        try {
            permissionDataInitializer.verifyTenantSeeded(company);
        } catch (RuntimeException e) {
            problems.add(e.getMessage());
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Tenant verification failed: " + String.join(",", problems));
        }
    }

    public PlatformAdminCompanyResponse activateCompany(String companyCode) {
        return withCompanyCodeDb(companyCode, () -> self.activateCompanyData(companyCode));
    }

    @Transactional
    public PlatformAdminCompanyResponse activateCompanyData(String companyCode) {
        Company company = requireCompany(companyCode);
        company.setActive(true);
        // Flush first: DB errors must surface BEFORE the registry sync runs,
        // otherwise a failed save leaves the two flags diverged.
        company = companyRepository.saveAndFlush(company);
        syncRegistryStatus(companyCode, true);
        return toCompanyResponse(company);
    }

    public PlatformAdminCompanyResponse deactivateCompany(String companyCode) {
        return withCompanyCodeDb(companyCode, () -> self.deactivateCompanyData(companyCode));
    }

    @Transactional
    public PlatformAdminCompanyResponse deactivateCompanyData(String companyCode) {
        Company company = requireCompany(companyCode);
        company.setActive(false);
        // Tenant save FIRST, registry sync second (same order as activate):
        // if the save fails, the registry stays untouched — no divergence.
        company = companyRepository.saveAndFlush(company);
        // Keep registry status in sync so routing/login follows company state.
        syncRegistryStatus(companyCode, false);
        return toCompanyResponse(company);
    }

    public PlatformAdminCompanyResponse setCompanyChatbotEnabled(String companyCode, boolean enabled) {
        return withCompanyCodeDb(companyCode, () -> self.setCompanyChatbotEnabledData(companyCode, enabled));
    }

    @Transactional
    public PlatformAdminCompanyResponse setCompanyChatbotEnabledData(String companyCode, boolean enabled) {
        Company company = requireCompany(companyCode);
        company.setChatbotEnabled(enabled);
        return toCompanyResponse(companyRepository.save(company));
    }

    /**
     * Reset the tenant super-admin password from Platform Admin.
     * Username defaults to the catalog value on first reset and is kept
     * afterwards; password is always BCrypt-hashed, never plain.
     */
    public PlatformAdminCompanyResponse resetSuperAdminPassword(String companyCode, String newPassword) {
        String password = newPassword == null ? "" : newPassword.trim();
        if (password.length() < 8) {
            throw new BadRequestException("Super-admin password must be at least 8 characters");
        }
        return withCompanyCodeDb(companyCode, () -> self.resetSuperAdminPasswordData(companyCode, password));
    }

    @Transactional
    public PlatformAdminCompanyResponse resetSuperAdminPasswordData(String companyCode, String newPassword) {
        Company company = requireCompany(companyCode);
        if (company.getSuperAdminUsername() == null || company.getSuperAdminUsername().isBlank()) {
            company.setSuperAdminUsername(com.billing.config.MasterSeedCatalog.DEFAULT_SUPER_ADMIN_USERNAME);
        }
        company.setSuperAdminPassword(passwordEncoder.encode(newPassword));
        return toCompanyResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public PlatformAdminCompanyDetailsResponse companyDetails(String companyCode) {
        return withCompanyCodeDb(companyCode, () -> self.companyDetailsData(companyCode));
    }

    @Transactional(readOnly = true)
    public PlatformAdminCompanyDetailsResponse companyDetailsData(String companyCode) {
        Company company = requireCompany(companyCode);
        List<User> users = userRepository.findAllByOrderByCreatedAtDesc();
        List<PlatformAdminUserResponse> mappedUsers = users.stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(User::getId, Comparator.reverseOrder()))
                .map(user -> toUserResponse(user, company))
                .toList();

        User owner = users.stream()
                .filter(user -> user.getRole() == RoleName.OWNER)
                .findFirst()
                .orElse(null);

        return PlatformAdminCompanyDetailsResponse.builder()
                .company(toCompanyResponse(company))
                .owner(owner == null ? null : toUserResponse(owner, company))
                .ownerCount(users.stream().filter(user -> user.getRole() == RoleName.OWNER).count())
                .adminCount(users.stream().filter(user -> user.getRole() == RoleName.ADMIN).count())
                .userCount(users.stream().filter(user -> user.getRole() == RoleName.USER).count())
                .auditLogCount(auditLogRepository.countByCompany(company))
                .users(mappedUsers)
                .build();
    }

    /**
     * Mirror the tenant companies.is_active flag into the registry status.
     * Matches on the registry database_name column using the database name
     * RESOLVED from the catalog for this company code — never on the tenant
     * row's own database_name field, which can be NULL or a stale placeholder
     * (that silent 0-row update once locked a super-admin out while the
     * registry still said ACTIVE). A missed sync is logged loudly instead of
     * being swallowed.
     */
    private void syncRegistryStatus(String companyCode, boolean active) {
        String code = companyCode == null ? "" : companyCode.trim().toUpperCase(Locale.ROOT);
        String status = active ? "ACTIVE" : "INACTIVE";
        String databaseName = null;
        try {
            databaseName = companyRegistryService.resolveDbNameByCodeAnyStatus(code);
        } catch (Exception e) {
            log.warn("[RegistrySync] could not resolve database for company_code={} (expected {}): {}",
                    code, status, e.getMessage());
        }
        if (databaseName == null || databaseName.isBlank()) {
            log.warn("[RegistrySync] no registry row for company_code={}; status NOT synced (expected {}). Add/fix the company_registry row.",
                    code, status);
            return;
        }
        try {
            int rows = billingCommonJdbcTemplate.update(
                    "UPDATE billing_common.company_registry SET status=? WHERE database_name=?",
                    status, databaseName.trim());
            if (rows == 0) {
                log.warn("[RegistrySync] UPDATE matched 0 rows for database_name={} (company_code={}, expected {}). Registry and tenant flags are DIVERGED — fix company_registry manually.",
                        databaseName, code, status);
            }
        } catch (Exception e) {
            log.warn("[RegistrySync] failed for company_code={} database_name={} (expected {}): {}",
                    code, databaseName, status, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PlatformAdminSettingsResponse settings() {
        return toSettingsResponse(requirePlatformSettingRow());
    }

    @Transactional
    public PlatformAdminSettingsResponse updateSettings(PlatformAdminSettingsRequest request) {
        Map<String, Object> row = requirePlatformSettingRow();
        Long id = row.get("id") instanceof Number number ? number.longValue() : null;
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE billing_common.platform_settings SET updated_at=NOW()");
        if (request.getPlatformName() != null) {
            sql.append(", platform_name=?");
            args.add(request.getPlatformName().trim());
        }
        if (request.getPlatformTagline() != null) {
            sql.append(", platform_tagline=?");
            args.add(blankToNull(request.getPlatformTagline()));
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            sql.append(", username=?");
            args.add(request.getUsername().trim());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            sql.append(", password=?");
            args.add(passwordEncoder.encode(request.getPassword().trim()));
        }
        sql.append(" WHERE id=?");
        args.add(id);
        billingCommonJdbcTemplate.update(sql.toString(), args.toArray());
        return toSettingsResponse(requirePlatformSettingRow());
    }

    @Transactional
    public PlatformAdminSettingsResponse updateSettings(Map<String, Object> param) {
        PlatformAdminSettingsRequest request = mapToPlatformAdminSettingsRequest(param);
        return updateSettings(request);
    }

    private Map<String, Object> requirePlatformSettingRow() {
        try {
            return billingCommonJdbcTemplate.queryForMap(
                    "SELECT id, platform_name, platform_logo, platform_tagline, username FROM billing_common.platform_settings ORDER BY id ASC LIMIT 1");
        } catch (Exception e) {
            throw new ResourceNotFoundException("Platform settings not found");
        }
    }

    private Company requireCompany(String companyCode) {
        String code = companyCode == null ? "" : companyCode.trim();
        if (code.isEmpty()) {
            throw new ResourceNotFoundException("Company not found");
        }
        return companyRepository.findByCodeIgnoreCase(code.toUpperCase(Locale.ROOT))
                .or(() -> companyRepository.findByCodeIgnoreCase(code))
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    /**
     * Run JPA work in a specific tenant database (resolved from the common catalog),
     * restoring the previous tenant afterwards. Thread-bound like the interceptor.
     */
    private <T> T withTenantDb(String databaseName, String dbHost, Supplier<T> work) {
        if (databaseName == null || databaseName.isBlank()) {
            throw new ResourceNotFoundException("Company not found");
        }
        requestInterceptor.ensureTenant(databaseName.trim(), dbHost);
        String previous = TenantContextHolder.getTenant();
        TenantContextHolder.setTenantId(databaseName.trim());
        try {
            return work.get();
        } finally {
            if (previous == null || previous.isBlank()) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.setTenantId(previous);
            }
        }
    }

    /**
     * Run JPA work in the tenant database for the given company code
     * (resolved via billing_common.company_registry.company_code).
     */
    private <T> T withCompanyCodeDb(String companyCode, Supplier<T> work) {
        String db = companyRegistryService.resolveDbNameByCodeAnyStatus(companyCode);
        if (db == null || db.isBlank()) {
            throw new ResourceNotFoundException("Company not found");
        }
        return withTenantDb(db, companyRegistryService.resolveDbHostByDatabase(db), work);
    }

    private String registryString(Object value) {
        return value == null ? null : value.toString();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0;
    }

    /**
     * Enrich a company_registry row with live data from its own tenant DB
     * (company entity + role counts). Falls back to registry columns only.
     * The tenant switch happens here; the JPA read runs in
     * enrichTenantRowData through the self proxy so its transaction starts
     * inside the switched tenant, not the entry tenant.
     */
    private PlatformAdminCompanyResponse enrichTenantRow(Map<String, Object> row) {
        String db = registryString(row.get("database_name"));
        if (db == null || db.isBlank()) {
            return fallbackTenantRow(row);
        }
        return withTenantDb(db, registryString(row.get("db_host")), () -> {
            try {
                return self.enrichTenantRowData(row);
            } catch (Exception e) {
                log.warn("[PlatformAdmin] tenant enrichment failed (company_code={}, db={}): {}",
                        registryString(row.get("company_code")), db,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                return fallbackTenantRow(row);
            }
        });
    }

    /**
     * JPA part of row enrichment. Enter only through the self proxy from
     * inside withTenantDb: the transaction (and its connection) then starts
     * AFTER the tenant switch — an outer @Transactional binds to the entry
     * tenant and every other tenant shows the fallback row ("Unknown").
     */
    @Transactional(readOnly = true)
    public PlatformAdminCompanyResponse enrichTenantRowData(Map<String, Object> row) {
        String code = registryString(row.get("company_code"));
        Company company = null;
        if (code != null) {
            company = companyRepository.findByCodeIgnoreCase(code).orElse(null);
        }
        if (company == null) {
            return fallbackTenantRow(row);
        }
        List<User> users = userRepository.findAllByOrderByCreatedAtDesc();
        String ownerName = users.stream()
                .filter(user -> user.getRole() == RoleName.OWNER)
                .map(User::getFullName)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        long ownerCount = users.stream().filter(user -> user.getRole() == RoleName.OWNER).count();
        long adminCount = users.stream().filter(user -> user.getRole() == RoleName.ADMIN).count();
        long userCount = users.stream().filter(user -> user.getRole() == RoleName.USER).count();
        return PlatformAdminCompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .code(company.getCode())
                .ownerName(ownerName)
                .email(company.getEmail())
                .mobile(company.getPhone())
                .active(company.isActive())
                .chatbotEnabled(company.isChatbotEnabled())
                .createdAt(company.getCreatedAt())
                .ownerCount(ownerCount)
                .adminCount(adminCount)
                .userCount(userCount)
                .totalUsers(users.size())
                .build();
    }

    private PlatformAdminCompanyResponse fallbackTenantRow(Map<String, Object> row) {
        return PlatformAdminCompanyResponse.builder()
                .name(registryString(row.get("company_name")))
                .code(registryString(row.get("company_code")))
                .active(!"INACTIVE".equalsIgnoreCase(registryString(row.get("status"))))
                .createdAt(null)
                .build();
    }

    private long[] countTenantUsers(Map<String, Object> row) {
        String db = registryString(row.get("database_name"));
        if (db == null || db.isBlank()) {
            return new long[]{0, 0, 0};
        }
        return withTenantDb(db, registryString(row.get("db_host")), () -> {
            try {
                return self.countTenantUsersData();
            } catch (Exception e) {
                log.warn("[PlatformAdmin] user count failed (db={}): {}", db,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                return new long[]{0, 0, 0};
            }
        });
    }

    /** JPA user counts — entered via self proxy after the tenant switch. */
    @Transactional(readOnly = true)
    public long[] countTenantUsersData() {
        List<User> users = userRepository.findAllByOrderByCreatedAtDesc();
        return new long[]{
                users.stream().filter(user -> user.getRole() == RoleName.OWNER).count(),
                users.stream().filter(user -> user.getRole() == RoleName.ADMIN).count(),
                users.stream().filter(user -> user.getRole() == RoleName.USER).count()
        };
    }

    private PlatformAdminCompanyResponse toCompanyResponse(Company company) {
        List<User> users = userRepository.findAllByOrderByCreatedAtDesc();
        String ownerName = users.stream()
                .filter(user -> user.getRole() == RoleName.OWNER)
                .map(User::getFullName)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        return PlatformAdminCompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .code(company.getCode())
                .ownerName(ownerName)
                .email(company.getEmail())
                .mobile(company.getPhone())
                .active(company.isActive())
                .chatbotEnabled(company.isChatbotEnabled())
                .createdAt(company.getCreatedAt())
                .totalUsers(users.size())
                .build();
    }

    private PlatformAdminUserResponse toUserResponse(User user, Company company) {
        return PlatformAdminUserResponse.builder()
                .id(user.getId())
                .companyId(company == null ? null : company.getId())
                .companyName(company == null ? null : company.getName())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .role(user.getRole().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private PlatformAdminSettingsResponse toSettingsResponse(Map<String, Object> row) {
        return PlatformAdminSettingsResponse.builder()
                .platformName(registryString(row.get("platform_name")))
                .platformLogo(registryString(row.get("platform_logo")))
                .platformTagline(registryString(row.get("platform_tagline")))
                .username(registryString(row.get("username")))
                .build();
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PlatformAdminSettingsRequest mapToPlatformAdminSettingsRequest(Map<String, Object> param) {
        PlatformAdminSettingsRequest request = new PlatformAdminSettingsRequest();
        String platformName = DataTypeUtility.stringValue(param.get("platformName"));
        if (platformName.length() == 0) {
            platformName = DataTypeUtility.stringValue(param.get("platform_name"));
        }
        if (platformName.length() > 0) {
            request.setPlatformName(platformName);
        }
        String platformTagline = DataTypeUtility.stringValue(param.get("platformTagline"));
        if (platformTagline.length() == 0) {
            platformTagline = DataTypeUtility.stringValue(param.get("platform_tagline"));
        }
        if (platformTagline.length() > 0) {
            request.setPlatformTagline(platformTagline);
        } else {
            if (param.containsKey("platformTagline") || param.containsKey("platform_tagline")) {
                request.setPlatformTagline(null);
            }
        }
        String username = DataTypeUtility.stringValue(param.get("username"));
        if (username.length() > 0) {
            request.setUsername(username);
        }
        String password = DataTypeUtility.stringValue(param.get("password"));
        if (password.length() > 0) {
            request.setPassword(password);
        }
        return request;
    }
}
