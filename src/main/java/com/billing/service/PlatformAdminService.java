package com.billing.service;

import com.billing.config.PermissionDataInitializer;
import com.billing.core.Registry;
import com.billing.dto.PageResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyCreateRequest;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billing.util.DataTypeUtility;
import java.nio.charset.StandardCharsets;
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
    private final ResourceLoader resourceLoader;
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

    @Transactional
    public PlatformAdminCompanyResponse createCompany(PlatformAdminCompanyCreateRequest request) {
        validateCreateRequest(request);
        // All routing keys come from Platform Admin: no auto-generation.
        String code = request.getCompanyCode().trim().toUpperCase(Locale.ROOT);
        String databaseName = request.getDatabaseName().trim().toLowerCase(Locale.ROOT);
        String domain = request.getDomain().trim().toLowerCase(Locale.ROOT);
        String gstNumber = blankToNull(request.getGstNumber());

        Company company = Company.builder()
                .name(request.getCompanyName().trim())
                .code(code)
                .databaseName(databaseName)
                .email(request.getEmail().trim())
                .phone(request.getMobile().trim())
                .address(request.getAddress().trim())
                .taxId(gstNumber)
                .active(true)
                .build();

        // 1) CREATE DATABASE + schema + registry row (common catalog, tenant-independent)
        String dbHost = createCompanyDatabaseAndRegistry(code, company.getName(), databaseName, domain);

        // 2) Company + owner + seeds go into the NEW tenant database (not the admin context)
        return withTenantDb(databaseName, dbHost, () -> {
            Company saved = companyRepository.save(company);

            validateUniqueUser(saved, request.getOwnerUsername(), request.getOwnerMobile(), request.getOwnerEmail(), null);

            User owner = User.builder()
                    .company(saved)
                    .fullName(request.getOwnerName().trim())
                    .username(request.getOwnerUsername().trim())
                    .mobileNumber(request.getOwnerMobile().trim())
                    .email(request.getOwnerEmail().trim())
                    .password(passwordEncoder.encode(request.getOwnerPassword()))
                    .role(RoleName.OWNER)
                    .active(true)
                    .build();
            userRepository.save(owner);

            permissionDataInitializer.seedPermissionsForCompany(saved);
            permissionDataInitializer.seedThemeForCompany(saved);
            permissionDataInitializer.seedDefaultNotificationChannels(saved);
            taxMasterService.createDefaultTaxesForCompany(saved);

            return toCompanyResponse(saved);
        });
    }

    private void validateCreateRequest(PlatformAdminCompanyCreateRequest request) {
        List<String> messages = new ArrayList<>();
        if (request.getCompanyName() == null || request.getCompanyName().isBlank()) {
            messages.add("Company name is required");
        }
        String code = request.getCompanyCode() == null ? "" : request.getCompanyCode().trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            messages.add("Company code is required");
        } else if (!code.matches("[A-Z0-9]{1,20}")) {
            messages.add("Company code must be 1-20 uppercase letters/digits");
        } else if (isReservedCompanyCode(code)) {
            messages.add("Company code is reserved");
        }
        String databaseName = request.getDatabaseName() == null ? "" : request.getDatabaseName().trim().toLowerCase(Locale.ROOT);
        if (databaseName.isEmpty()) {
            messages.add("Database name is required");
        } else if (!databaseName.matches("[a-z0-9_]+")) {
            messages.add("Database name must be lowercase letters/digits/underscore");
        }
        if (request.getDomain() == null || request.getDomain().isBlank()) {
            messages.add("Domain is required");
        }
        if (request.getAddress() == null || request.getAddress().isBlank()) {
            messages.add("Address is required");
        }
        if (request.getMobile() == null || request.getMobile().isBlank()) {
            messages.add("Mobile is required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            messages.add("Company email is required");
        }
        if (request.getOwnerName() == null || request.getOwnerName().isBlank()) {
            messages.add("Owner name is required");
        }
        if (request.getOwnerUsername() == null || request.getOwnerUsername().isBlank()) {
            messages.add("Owner username is required");
        }
        if (request.getOwnerEmail() == null || request.getOwnerEmail().isBlank()) {
            messages.add("Owner email is required");
        }
        if (request.getOwnerMobile() == null || request.getOwnerMobile().isBlank()) {
            messages.add("Owner mobile is required");
        }
        if (request.getOwnerPassword() == null || request.getOwnerPassword().length() < 8) {
            messages.add("Owner password must be at least 8 characters");
        }
        if (!messages.isEmpty()) {
            throw new BadRequestException(String.join(" ", messages));
        }
    }

    @Transactional
    public PlatformAdminCompanyResponse createCompany(Map<String, Object> param) {
        PlatformAdminCompanyCreateRequest request = mapToCompanyCreateRequest(param);
        return createCompany(request);
    }

    @Transactional
    public PlatformAdminCompanyResponse activateCompany(String companyCode) {
        return withCompanyCodeDb(companyCode, () -> {
            Company company = requireCompany(companyCode);
            company.setActive(true);
            company = companyRepository.save(company);
            syncRegistryStatus(company);
            return toCompanyResponse(company);
        });
    }

    @Transactional
    public PlatformAdminCompanyResponse deactivateCompany(String companyCode) {
        return withCompanyCodeDb(companyCode, () -> {
            Company company = requireCompany(companyCode);
            company.setActive(false);
            // Keep registry status in sync so routing/login follows company state.
            syncRegistryStatus(company);
            return toCompanyResponse(companyRepository.save(company));
        });
    }

    @Transactional
    public PlatformAdminCompanyResponse setCompanyChatbotEnabled(String companyCode, boolean enabled) {
        return withCompanyCodeDb(companyCode, () -> {
            Company company = requireCompany(companyCode);
            company.setChatbotEnabled(enabled);
            return toCompanyResponse(companyRepository.save(company));
        });
    }

    @Transactional(readOnly = true)
    public PlatformAdminCompanyDetailsResponse companyDetails(String companyCode) {
        return withCompanyCodeDb(companyCode, () -> {
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
        });
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

    private void validateUniqueUser(Company company, String username, String mobileNumber, String email, Long currentUserId) {
        List<String> messages = new ArrayList<>();
        String normalizedUsername = username == null ? null : username.trim();
        String normalizedMobile = mobileNumber == null ? null : mobileNumber.trim();
        String normalizedEmail = email == null ? null : email.trim();

        userRepository.findByUsernameIgnoreCase( normalizedUsername)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Username already exists in this company."));

        userRepository.findByMobileNumber( normalizedMobile)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Mobile number already exists in this company."));

        userRepository.findByEmailIgnoreCase( normalizedEmail)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Email already exists in this company."));

        if (!messages.isEmpty()) {
            throw new BadRequestException(String.join(" ", messages));
        }
    }

    /**
     * TSM-like tenant provisioning: CREATE DATABASE + schema + INSERT company_registry.
     * All routing keys (code/database/domain) come from Platform Admin: no auto-generation.
     * Returns the db host used (for tenant pool creation + JPA work in the new DB).
     */
    public String createCompanyDatabaseAndRegistry(String companyCode, String companyName, String databaseName, String domain) {
        String code = companyCode == null ? "" : companyCode.trim().toUpperCase(Locale.ROOT);
        String db = databaseName == null ? "" : databaseName.trim().toLowerCase(Locale.ROOT);
        String liveDomain = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty() || !code.matches("[A-Z0-9]{1,20}") || isReservedCompanyCode(code)) {
            throw new BadRequestException("Invalid company code for database provisioning");
        }
        if (db.isEmpty() || !db.matches("[a-z0-9_]+")) {
            throw new BadRequestException("Invalid database name for database provisioning");
        }
        if (liveDomain.isEmpty()) {
            throw new BadRequestException("Domain is required for database provisioning");
        }
        // Global uniqueness (common catalog is the single source of truth across all DBs).
        if (companyRegistryService.resolveDbName(code) != null) {
            throw new BadRequestException("Company code already exists");
        }
        try {
            Integer dbTaken = billingCommonJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM billing_common.company_registry WHERE database_name=?",
                    Integer.class, db);
            if (dbTaken != null && dbTaken > 0) {
                throw new BadRequestException("Company database already exists");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception ignored) {
            // table missing etc. -> fail later with clear error
        }
        try {
            Integer domainTaken = billingCommonJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM billing_common.company_registry WHERE domain=?",
                    Integer.class, liveDomain);
            if (domainTaken != null && domainTaken > 0) {
                throw new BadRequestException("Domain already exists");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception ignored) {
            // table missing etc. -> fail later with clear error
        }
        String dbHost = resolveProvisionHost();
        billingCommonJdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        try {
            applyTenantSchema(db, dbHost);
        } catch (RuntimeException e) {
            // Don't leave an empty orphan DB behind; retry will CREATE fresh again.
            try {
                billingCommonJdbcTemplate.execute("DROP DATABASE IF EXISTS `" + db + "`");
            } catch (Exception ignored) {
                // best-effort cleanup; original error is authoritative
            }
            throw e;
        }
        requestInterceptor.ensureTenant(db, dbHost);
        String sql = "INSERT INTO billing_common.company_registry (company_code, company_name, database_name, domain, db_host, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
        String name = companyName == null ? code : companyName.trim();
        // NOTE: database_name is UNIQUE -> one row per tenant. Local dev resolves via
        // X-Company-Code header (company_code lookup) + CompanyDomainInfo static map.
        billingCommonJdbcTemplate.update(sql, code, name, db, liveDomain, dbHost);
        return dbHost;
    }

    private String resolveProvisionHost() {
        String dbHost = Registry.dbmap.getOrDefault("url", "localhost:3306");
        if (dbHost == null || dbHost.isBlank()) {
            dbHost = "localhost:3306";
        }
        return dbHost.trim().replace("jdbc:mysql://", "").replace("/", "");
    }

    /**
     * Create all tenant tables in a fresh database from the versioned schema
     * template (classpath:db/tenant/tenant_schema.sql). Idempotent.
     */
    private void applyTenantSchema(String db, String dbHost) {
        String hostPort = (dbHost == null || dbHost.isBlank())
                ? resolveProvisionHost()
                : dbHost.trim().replace("jdbc:mysql://", "").replace("/", "");
        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + hostPort + "/" + db
                + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Kolkata");
        config.setUsername(Registry.dbmap.getOrDefault("username", "root"));
        config.setPassword(Registry.dbmap.getOrDefault("password", "root"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setPoolName(db + "-provision");
        config.setConnectionTimeout(15000);
        String schema = loadTenantSchemaSql();
        // Strip UTF-8 BOM if present (mysqldump / editor generated files start with U+FEFF,
        // which MySQL reports as "near '?-- Tenant schema...'").
        if (!schema.isEmpty() && schema.charAt(0) == '\uFEFF') {
            schema = schema.substring(1);
        }
        schema = schema.replace("\uFEFF", "");
        // Drop full-line comments so header comments never reach MySQL.
        // (Split on ';' is safe here: tenant_schema.sql holds plain CREATE TABLEs,
        // no procedures/triggers with embedded semicolons.)
        StringBuilder clean = new StringBuilder();
        for (String line : schema.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()
                    || trimmed.startsWith("--")
                    || trimmed.startsWith("#")
                    || trimmed.startsWith("//")
                    || trimmed.startsWith("/*")
                    || trimmed.startsWith("*")) {
                continue;
            }
            clean.append(line).append('\n');
        }
        try (com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource(config)) {
            JdbcTemplate template = new JdbcTemplate(ds);
            template.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                for (String stmt : clean.toString().split(";")) {
                    String trimmed = stmt == null ? "" : stmt.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    template.execute(trimmed);
                }
            } finally {
                try {
                    template.execute("SET FOREIGN_KEY_CHECKS=1");
                } catch (Exception ignored) {
                    // best-effort restore; provisioning result is decided by the loop above
                }
            }
        }
    }

    private String loadTenantSchemaSql() {
        try {
            Resource resource = resourceLoader.getResource("classpath:db/tenant/tenant_schema.sql");
            try (java.io.InputStream in = resource.getInputStream()) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                    content = content.substring(1);
                }
                return content;
            }
        } catch (java.io.IOException e) {
            throw new BadRequestException("Tenant schema template is missing. Please contact administrator.");
        }
    }

    /**
     * Reserved words that would collide with platform-admin path segments
     * (/companies/{companyCode}/**, /companies/overview, /dashboard, /settings).
     */
    private static final java.util.Set<String> RESERVED_COMPANY_CODES = java.util.Set.of(
            "OVERVIEW", "DASHBOARD", "SETTINGS", "COMMUNICATION", "COMPANIES",
            "LOGIN", "ACTIVATE", "DEACTIVATE", "CHATBOT", "API");

    private boolean isReservedCompanyCode(String code) {
        return code != null && RESERVED_COMPANY_CODES.contains(code.trim().toUpperCase(Locale.ROOT));
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

    private PlatformAdminCompanyCreateRequest mapToCompanyCreateRequest(Map<String, Object> param) {
        PlatformAdminCompanyCreateRequest request = new PlatformAdminCompanyCreateRequest();
        String companyName = DataTypeUtility.stringValue(param.get("companyName"));
        if (companyName.length() == 0) {
            companyName = DataTypeUtility.stringValue(param.get("company_name"));
        }
        if (companyName.length() == 0) {
            companyName = DataTypeUtility.stringValue(param.get("name"));
        }
        request.setCompanyName(companyName);
        String companyCode = DataTypeUtility.stringValue(param.get("companyCode"));
        if (companyCode.length() == 0) {
            companyCode = DataTypeUtility.stringValue(param.get("company_code"));
        }
        if (companyCode.length() == 0) {
            companyCode = DataTypeUtility.stringValue(param.get("code"));
        }
        request.setCompanyCode(companyCode);
        String databaseName = DataTypeUtility.stringValue(param.get("databaseName"));
        if (databaseName.length() == 0) {
            databaseName = DataTypeUtility.stringValue(param.get("database_name"));
        }
        request.setDatabaseName(databaseName);
        String domain = DataTypeUtility.stringValue(param.get("domain"));
        request.setDomain(domain);
        String address = DataTypeUtility.stringValue(param.get("address"));
        if (address.length() == 0) {
            address = null;
        }
        request.setAddress(address);
        String gstNumber = DataTypeUtility.stringValue(param.get("gstNumber"));
        if (gstNumber.length() == 0) {
            gstNumber = DataTypeUtility.stringValue(param.get("gst_number"));
        }
        if (gstNumber.length() == 0) {
            gstNumber = DataTypeUtility.stringValue(param.get("taxId"));
        }
        if (gstNumber.length() == 0) {
            gstNumber = null;
        }
        request.setGstNumber(gstNumber);
        String mobile = DataTypeUtility.stringValue(param.get("mobile"));
        if (mobile.length() == 0) {
            mobile = DataTypeUtility.stringValue(param.get("phone"));
        }
        if (mobile.length() == 0) {
            mobile = null;
        }
        request.setMobile(mobile);
        String email = DataTypeUtility.stringValue(param.get("email"));
        if (email.length() == 0) {
            email = null;
        }
        request.setEmail(email);
        String ownerName = DataTypeUtility.stringValue(param.get("ownerName"));
        if (ownerName.length() == 0) {
            ownerName = DataTypeUtility.stringValue(param.get("owner_name"));
        }
        if (ownerName.length() == 0) {
            ownerName = null;
        }
        request.setOwnerName(ownerName);
        String ownerUsername = DataTypeUtility.stringValue(param.get("ownerUsername"));
        if (ownerUsername.length() == 0) {
            ownerUsername = DataTypeUtility.stringValue(param.get("owner_username"));
        }
        if (ownerUsername.length() == 0) {
            ownerUsername = null;
        }
        request.setOwnerUsername(ownerUsername);
        String ownerEmail = DataTypeUtility.stringValue(param.get("ownerEmail"));
        if (ownerEmail.length() == 0) {
            ownerEmail = DataTypeUtility.stringValue(param.get("owner_email"));
        }
        if (ownerEmail.length() == 0) {
            ownerEmail = null;
        }
        request.setOwnerEmail(ownerEmail);
        String ownerMobile = DataTypeUtility.stringValue(param.get("ownerMobile"));
        if (ownerMobile.length() == 0) {
            ownerMobile = DataTypeUtility.stringValue(param.get("owner_mobile"));
        }
        if (ownerMobile.length() == 0) {
            ownerMobile = null;
        }
        request.setOwnerMobile(ownerMobile);
        String ownerPassword = DataTypeUtility.stringValue(param.get("ownerPassword"));
        if (ownerPassword.length() == 0) {
            ownerPassword = DataTypeUtility.stringValue(param.get("owner_password"));
        }
        if (ownerPassword.length() == 0) {
            ownerPassword = null;
        }
        request.setOwnerPassword(ownerPassword);
        return request;
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
