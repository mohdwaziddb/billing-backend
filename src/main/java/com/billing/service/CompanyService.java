package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.CompanyOwner;
import com.billing.entity.CompanyThemeSetting;
import com.billing.entity.User;
import com.billing.dto.company.CompanyOwnerResponse;
import com.billing.dto.company.CompanyOwnersRequest;
import com.billing.dto.company.CompanySettingsRequest;
import com.billing.dto.company.CompanyThemeRequest;
import com.billing.dto.company.CompanyThemeResponse;
import com.billing.dto.user.CompanySummary;
import com.billing.exception.BadRequestException;
import com.billing.repository.CompanyOwnerRepository;
import com.billing.repository.CompanyRepository;
import com.billing.repository.CompanyThemeSettingRepository;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final AccessControlService accessControlService;
    private final CompanyRepository companyRepository;
    private final CompanyOwnerRepository companyOwnerRepository;
    private final CompanyThemeSettingRepository companyThemeSettingRepository;
    private final UserRepository userRepository;
    private static final Set<String> LOGO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public CompanySummary getSettings(String email) {
        return toSummary(accessControlService.getCurrentCompany(email));
    }

    @Transactional
    public CompanySummary updateSettings(String email, CompanySettingsRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        if (!company.getEmail().equalsIgnoreCase(request.getEmail())
                && companyRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("Company email already exists");
        }
        if (!company.getTaxId().equalsIgnoreCase(request.getTaxId())
                && companyRepository.existsByTaxIdIgnoreCase(request.getTaxId())) {
            throw new BadRequestException("Tax ID already exists");
        }

        company.setName(request.getName());
        company.setLegalName(blankToNull(request.getLegalName()));
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());
        company.setAlternatePhone(blankToNull(request.getAlternatePhone()));
        company.setAddress(firstNonBlank(request.getAddress(), request.getAddressLine1()));
        company.setAddressLine1(blankToNull(request.getAddressLine1()));
        company.setAddressLine2(blankToNull(request.getAddressLine2()));
        company.setCity(blankToNull(request.getCity()));
        company.setState(blankToNull(request.getState()));
        company.setCountry(blankToNull(request.getCountry()));
        company.setPincode(blankToNull(request.getPincode()));
        company.setTaxId(request.getTaxId());
        company.setPanNumber(blankToNull(request.getPanNumber()));
        company.setCinNumber(blankToNull(request.getCinNumber()));
        company.setWebsiteUrl(blankToNull(request.getWebsiteUrl()));
        company.setDatabaseName(blankToNull(request.getDatabaseName()));

        return toSummary(companyRepository.save(company));
    }

    @Transactional
    public CompanySummary uploadLogo(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Logo file is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!LOGO_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Only JPG, PNG, and WEBP logos are allowed");
        }

        Company company = accessControlService.requireOwnerCompany(email);
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String fileName = "company-" + company.getId() + "-" + UUID.randomUUID() + extension;
        Path targetDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("company-logos");
        Path targetFile = targetDir.resolve(fileName).normalize();
        if (!targetFile.startsWith(targetDir)) {
            throw new BadRequestException("Invalid logo path");
        }

        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BadRequestException("Unable to upload company logo");
        }

        company.setLogoUrl("/uploads/company-logos/" + fileName);
        return toSummary(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyOwnerResponse> owners(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return companyOwnerRepository.findByCompanyOrderByCreatedAtAsc(company).stream()
                .map(CompanyOwner::getUser)
                .map(this::toOwnerResponse)
                .toList();
    }

    @Transactional
    public List<CompanyOwnerResponse> updateOwners(String email, CompanyOwnersRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        Set<Long> ownerIds = new HashSet<>(request.getOwnerUserIds());
        if (ownerIds.isEmpty()) {
            throw new BadRequestException("Company must have at least one active owner");
        }
        List<User> selectedUsers = ownerIds.stream()
                .map(userId -> userRepository.findByIdAndCompany(userId, company)
                        .orElseThrow(() -> new BadRequestException("Selected owner user not found in this company")))
                .toList();
        if (selectedUsers.stream().noneMatch(User::isActive)) {
            throw new BadRequestException("Company must have at least one active owner");
        }

        for (CompanyOwner existing : companyOwnerRepository.findByCompanyOrderByCreatedAtAsc(company)) {
            if (!ownerIds.contains(existing.getUser().getId())) {
                companyOwnerRepository.delete(existing);
            }
        }
        for (User user : selectedUsers) {
            companyOwnerRepository.findByCompanyAndUser(company, user)
                    .orElseGet(() -> companyOwnerRepository.save(CompanyOwner.builder()
                            .company(company)
                            .user(user)
                            .build()));
        }
        return owners(email);
    }

    @Transactional(readOnly = true)
    public CompanyThemeResponse theme(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        CompanyThemeSetting setting = companyThemeSettingRepository.findByCompany(company)
                .orElseGet(() -> CompanyThemeSetting.builder().company(company).themeColor("#0EA5E9").build());
        return toThemeResponse(setting);
    }

    @Transactional
    public CompanyThemeResponse updateTheme(String email, CompanyThemeRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        CompanyThemeSetting setting = companyThemeSettingRepository.findByCompany(company)
                .orElseGet(() -> CompanyThemeSetting.builder().company(company).build());
        setting.setThemeColor(request.getThemeColor().toUpperCase(Locale.ROOT));
        return toThemeResponse(companyThemeSettingRepository.save(setting));
    }

    @Transactional
    public CompanyThemeResponse resetTheme(String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        CompanyThemeSetting setting = companyThemeSettingRepository.findByCompany(company)
                .orElseGet(() -> CompanyThemeSetting.builder().company(company).build());
        setting.setThemeColor("#0EA5E9");
        return toThemeResponse(companyThemeSettingRepository.save(setting));
    }

    private CompanySummary toSummary(Company company) {
        return CompanySummary.builder()
                .id(company.getId())
                .name(company.getName())
                .legalName(company.getLegalName())
                .code(company.getCode())
                .databaseName(company.getDatabaseName())
                .email(company.getEmail())
                .phone(company.getPhone())
                .alternatePhone(company.getAlternatePhone())
                .address(company.getAddress())
                .addressLine1(company.getAddressLine1())
                .addressLine2(company.getAddressLine2())
                .city(company.getCity())
                .state(company.getState())
                .country(company.getCountry())
                .pincode(company.getPincode())
                .taxId(company.getTaxId())
                .panNumber(company.getPanNumber())
                .cinNumber(company.getCinNumber())
                .logoUrl(company.getLogoUrl())
                .websiteUrl(company.getWebsiteUrl())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = blankToNull(preferred);
        return normalized == null ? blankToNull(fallback) : normalized;
    }

    private CompanyOwnerResponse toOwnerResponse(User user) {
        return CompanyOwnerResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .role(user.getRole().name())
                .active(user.isActive())
                .build();
    }

    private CompanyThemeResponse toThemeResponse(CompanyThemeSetting setting) {
        return CompanyThemeResponse.builder()
                .themeColor(setting.getThemeColor())
                .build();
    }
}
