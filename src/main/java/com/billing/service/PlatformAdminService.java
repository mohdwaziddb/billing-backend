package com.billing.service;

import com.billing.config.PermissionDataInitializer;
import com.billing.dto.PageResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyCreateRequest;
import com.billing.dto.platformadmin.PlatformAdminCompanyDetailsResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyOverviewResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyOverviewView;
import com.billing.dto.platformadmin.PlatformAdminCompanyResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanySummaryView;
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
import com.billing.repository.AuditLogRepository;
import com.billing.repository.CompanyRepository;
import com.billing.repository.PlatformSettingRepository;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PlatformSettingRepository platformSettingRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionDataInitializer permissionDataInitializer;

    @Transactional(readOnly = true)
    public PlatformAdminDashboardResponse dashboard() {
        List<Company> companies = companyRepository.findAll();

        return PlatformAdminDashboardResponse.builder()
                .totalCompanies(companies.size())
                .activeCompanies(companies.stream().filter(Company::isActive).count())
                .inactiveCompanies(companies.stream().filter(company -> !company.isActive()).count())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<PlatformAdminCompanyResponse> companies(int page, int size, String search, Boolean active) {
        int resolvedPage = Math.max(0, page);
        int resolvedSize = Math.max(1, Math.min(size, 100));
        Page<PlatformAdminCompanySummaryView> pageResult = companyRepository.searchPlatformAdminCompanies(
                normalizeSearch(search),
                active,
                PageRequest.of(resolvedPage, resolvedSize)
        );
        return PageResponse.from(pageResult.map(this::toCompanyResponse));
    }

    @Transactional(readOnly = true)
    public PlatformAdminCompanyOverviewResponse companyOverview(String search, Boolean active) {
        PlatformAdminCompanyOverviewView overview = companyRepository.getPlatformAdminCompanyOverview(normalizeSearch(search), active);
        return PlatformAdminCompanyOverviewResponse.builder()
                .companyCount(overview == null || overview.getCompanyCount() == null ? 0 : overview.getCompanyCount())
                .ownerCount(overview == null || overview.getOwnerCount() == null ? 0 : overview.getOwnerCount())
                .adminCount(overview == null || overview.getAdminCount() == null ? 0 : overview.getAdminCount())
                .userCount(overview == null || overview.getUserCount() == null ? 0 : overview.getUserCount())
                .build();
    }

    @Transactional
    public PlatformAdminCompanyResponse createCompany(PlatformAdminCompanyCreateRequest request) {
        if (companyRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("Company email already exists");
        }
        String gstNumber = blankToNull(request.getGstNumber());
        if (gstNumber != null && companyRepository.existsByTaxIdIgnoreCase(gstNumber)) {
            throw new BadRequestException("Tax ID already exists");
        }

        Company company = Company.builder()
                .name(request.getCompanyName().trim())
                .code(normalizeCompanyCode(request.getCompanyName()))
                .email(request.getEmail().trim())
                .phone(request.getMobile().trim())
                .address(request.getAddress().trim())
                .taxId(gstNumber)
                .active(true)
                .build();
        company = companyRepository.save(company);

        validateUniqueUser(company, request.getOwnerUsername(), request.getOwnerMobile(), request.getOwnerEmail(), null);

        User owner = User.builder()
                .company(company)
                .fullName(request.getOwnerName().trim())
                .username(request.getOwnerUsername().trim())
                .mobileNumber(request.getOwnerMobile().trim())
                .email(request.getOwnerEmail().trim())
                .password(passwordEncoder.encode(request.getOwnerPassword()))
                .role(RoleName.OWNER)
                .active(true)
                .build();
        userRepository.save(owner);

        permissionDataInitializer.seedPermissionsForCompany(company);
        permissionDataInitializer.seedThemeForCompany(company);
        permissionDataInitializer.seedDefaultNotificationChannels(company);

        return toCompanyResponse(company);
    }

    @Transactional
    public PlatformAdminCompanyResponse activateCompany(Long companyId) {
        Company company = requireCompany(companyId);
        company.setActive(true);
        return toCompanyResponse(companyRepository.save(company));
    }

    @Transactional
    public PlatformAdminCompanyResponse deactivateCompany(Long companyId) {
        Company company = requireCompany(companyId);
        company.setActive(false);
        return toCompanyResponse(companyRepository.save(company));
    }

    @Transactional
    public PlatformAdminCompanyResponse setCompanyChatbotEnabled(Long companyId, boolean enabled) {
        Company company = requireCompany(companyId);
        company.setChatbotEnabled(enabled);
        return toCompanyResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public PlatformAdminCompanyDetailsResponse companyDetails(Long companyId) {
        Company company = requireCompany(companyId);
        List<User> users = userRepository.findByCompanyOrderByCreatedAtDesc(company);
        List<PlatformAdminUserResponse> mappedUsers = users.stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(User::getId, Comparator.reverseOrder()))
                .map(this::toUserResponse)
                .toList();

        User owner = users.stream()
                .filter(user -> user.getRole() == RoleName.OWNER)
                .findFirst()
                .orElse(null);

        return PlatformAdminCompanyDetailsResponse.builder()
                .company(toCompanyResponse(company))
                .owner(owner == null ? null : toUserResponse(owner))
                .ownerCount(users.stream().filter(user -> user.getRole() == RoleName.OWNER).count())
                .adminCount(users.stream().filter(user -> user.getRole() == RoleName.ADMIN).count())
                .userCount(users.stream().filter(user -> user.getRole() == RoleName.USER).count())
                .auditLogCount(auditLogRepository.countByCompany(company))
                .users(mappedUsers)
                .build();
    }

    @Transactional(readOnly = true)
    public PlatformAdminSettingsResponse settings() {
        return toSettingsResponse(requirePlatformSetting());
    }

    @Transactional
    public PlatformAdminSettingsResponse updateSettings(PlatformAdminSettingsRequest request) {
        PlatformSetting setting = requirePlatformSetting();
        if (request.getPlatformName() != null) {
            setting.setPlatformName(request.getPlatformName().trim());
        }
        if (request.getPlatformTagline() != null) {
            setting.setPlatformTagline(blankToNull(request.getPlatformTagline()));
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            setting.setUsername(request.getUsername().trim());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            setting.setPassword(passwordEncoder.encode(request.getPassword().trim()));
        }
        return toSettingsResponse(platformSettingRepository.save(setting));
    }

    private PlatformSetting requirePlatformSetting() {
        return platformSettingRepository.findTopByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("Platform settings not found"));
    }

    private Company requireCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    private PlatformAdminCompanyResponse toCompanyResponse(Company company) {
        List<User> users = userRepository.findByCompanyOrderByCreatedAtDesc(company);
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
                .ownerName(ownerName)
                .email(company.getEmail())
                .mobile(company.getPhone())
                .active(company.isActive())
                .chatbotEnabled(company.isChatbotEnabled())
                .createdAt(company.getCreatedAt())
                .totalUsers(users.size())
                .build();
    }

    private PlatformAdminCompanyResponse toCompanyResponse(PlatformAdminCompanySummaryView company) {
        long ownerCount = company.getOwnerCount() == null ? 0 : company.getOwnerCount();
        long adminCount = company.getAdminCount() == null ? 0 : company.getAdminCount();
        long userCount = company.getUserCount() == null ? 0 : company.getUserCount();
        return PlatformAdminCompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .ownerName(company.getOwnerName())
                .email(company.getEmail())
                .mobile(company.getMobile())
                .active(Boolean.TRUE.equals(company.getActive()))
                .chatbotEnabled(Boolean.TRUE.equals(company.getChatbotEnabled()))
                .createdAt(company.getCreatedAt())
                .ownerCount(ownerCount)
                .adminCount(adminCount)
                .userCount(userCount)
                .totalUsers(userCount)
                .build();
    }

    private PlatformAdminUserResponse toUserResponse(User user) {
        return PlatformAdminUserResponse.builder()
                .id(user.getId())
                .companyId(user.getCompany() == null ? null : user.getCompany().getId())
                .companyName(user.getCompany() == null ? null : user.getCompany().getName())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .role(user.getRole().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
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

        userRepository.findByCompanyAndUsernameIgnoreCase(company, normalizedUsername)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Username already exists in this company."));

        userRepository.findByCompanyAndMobileNumber(company, normalizedMobile)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Mobile number already exists in this company."));

        userRepository.findByCompanyAndEmailIgnoreCase(company, normalizedEmail)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> messages.add("Email already exists in this company."));

        if (!messages.isEmpty()) {
            throw new BadRequestException(String.join(" ", messages));
        }
    }

    private String normalizeCompanyCode(String companyName) {
        String base = companyName == null ? "" : companyName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (base.isBlank()) {
            base = "COMPANY";
        }
        String candidate = base.length() > 20 ? base.substring(0, 20) : base;
        if (!companyRepository.existsByCodeIgnoreCase(candidate)) {
            return candidate;
        }
        for (int attempt = 2; attempt < 10_000; attempt++) {
            String suffix = String.valueOf(attempt);
            int maxPrefixLength = Math.max(1, 20 - suffix.length());
            String next = candidate.substring(0, Math.min(candidate.length(), maxPrefixLength)) + suffix;
            if (!companyRepository.existsByCodeIgnoreCase(next)) {
                return next;
            }
        }
        throw new BadRequestException("Unable to generate unique company code");
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
}
