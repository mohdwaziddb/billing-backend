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
import com.billing.entity.PlatformSetting;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.util.DataTypeUtility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionDataInitializer permissionDataInitializer;
    private final TaxMasterService taxMasterService;
    private final CompanyRegistryService companyRegistryService;
    private final RequestInterceptor requestInterceptor;
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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
        verifyProvisionedTenant(company);
        return toCompanyResponse(company);
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
        company = companyRepository.save(company);
        syncRegistryStatus(company);
        return toCompanyResponse(company);
    }

    public PlatformAdminCompanyResponse deactivateCompany(String companyCode) {
        return withCompanyCodeDb(companyCode, () -> self.deactivateCompanyData(companyCode));
    }

    @Transactional
    public PlatformAdminCompanyResponse deactivateCompanyData(String companyCode) {
        Company company = requireCompany(companyCode);
        company.setActive(false);
        // Keep registry status in sync so routing/login follows company state.
        syncRegistryStatus(company);
        return toCompanyResponse(companyRepository.save(company));
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

    private void syncRegistryStatus(Company company) {
        try {
            String status = company.isActive() ? "ACTIVE" : "INACTIVE";
            billingCommonJdbcTemplate.update(
                    "UPDATE billing_common.company_registry SET status=? WHERE database_name=?",
                    status, company.getDatabaseName());
        } catch (Exception ignored) {
            // registry sync is best-effort; company state is authoritative in tenant DB
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
     */
    private PlatformAdminCompanyResponse enrichTenantRow(Map<String, Object> row) {
        String db = registryString(row.get("database_name"));
        String code = registryString(row.get("company_code"));
        String status = registryString(row.get("status"));
        boolean active = !"INACTIVE".equalsIgnoreCase(status);
        if (db == null || db.isBlank()) {
            return PlatformAdminCompanyResponse.builder()
                    .name(registryString(row.get("company_name")))
                    .code(code)
                    .active(active)
                    .createdAt(null)
                    .build();
        }
        return withTenantDb(db, registryString(row.get("db_host")), () -> {
            Company company = null;
            try {
                if (code != null) {
                    company = companyRepository.findByCodeIgnoreCase(code).orElse(null);
                }
            } catch (Exception ignored) {
                company = null;
            }
            if (company == null) {
                return PlatformAdminCompanyResponse.builder()
                        .name(registryString(row.get("company_name")))
                        .code(code)
                        .active(active)
                        .build();
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
        });
    }

    private long[] countTenantUsers(Map<String, Object> row) {
        String db = registryString(row.get("database_name"));
        if (db == null || db.isBlank()) {
            return new long[]{0, 0, 0};
        }
        return withTenantDb(db, registryString(row.get("db_host")), () -> {
            List<User> users;
            try {
                users = userRepository.findAllByOrderByCreatedAtDesc();
            } catch (Exception ignored) {
                return new long[]{0, 0, 0};
            }
            return new long[]{
                    users.stream().filter(user -> user.getRole() == RoleName.OWNER).count(),
                    users.stream().filter(user -> user.getRole() == RoleName.ADMIN).count(),
                    users.stream().filter(user -> user.getRole() == RoleName.USER).count()
            };
        });
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

    private PlatformAdminSettingsResponse toSettingsResponse(PlatformSetting setting) {
        return PlatformAdminSettingsResponse.builder()
                .platformName(setting.getPlatformName())
                .platformLogo(setting.getPlatformLogo())
                .platformTagline(setting.getPlatformTagline())
                .username(setting.getUsername())
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
