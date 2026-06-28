package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.CompanyThemeSetting;
import com.billing.dto.company.CompanySettingsRequest;
import com.billing.dto.company.CompanyThemeRequest;
import com.billing.dto.company.CompanyThemeResponse;
import com.billing.dto.user.CompanySummary;
import com.billing.exception.BadRequestException;
import com.billing.repository.CompanyRepository;
import com.billing.repository.CompanyThemeSettingRepository;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final AccessControlService accessControlService;
    private final CompanyRepository companyRepository;
    private final CompanyThemeSettingRepository companyThemeSettingRepository;
    private final StateMasterService stateMasterService;
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
        boolean gstRegistered = Boolean.TRUE.equals(request.getGstRegistered());
        String requestedGstin = firstNonBlank(request.getGstin(), request.getTaxId());
        if (gstRegistered && requestedGstin == null) {
            throw new BadRequestException("GST number is required when GST Registered is Yes");
        }
        if (!gstRegistered) {
            requestedGstin = null;
        }
        String currentGstin = firstNonBlank(company.getGstin(), company.getTaxId());
        if (requestedGstin != null
                && (currentGstin == null || !currentGstin.equalsIgnoreCase(requestedGstin))
                && companyRepository.existsByTaxIdIgnoreCase(requestedGstin)) {
            throw new BadRequestException("Tax ID already exists");
        }

        var stateMaster = request.getStateId() == null ? null : stateMasterService.getActiveByIdOrThrow(request.getStateId(), "Company state");

        company.setName(request.getName());
        company.setLegalName(blankToNull(request.getLegalName()));
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());
        company.setAlternatePhone(blankToNull(request.getAlternatePhone()));
        company.setAddress(firstNonBlank(request.getAddress(), request.getAddressLine1()));
        company.setAddressLine1(blankToNull(request.getAddressLine1()));
        company.setAddressLine2(blankToNull(request.getAddressLine2()));
        company.setCity(blankToNull(request.getCity()));
        company.setStateMaster(stateMaster);
        company.setState(stateMaster != null ? stateMaster.getStateName() : blankToNull(request.getState()));
        company.setCountry(stateMaster != null ? stateMaster.getCountryName() : blankToNull(request.getCountry()));
        company.setPincode(blankToNull(request.getPincode()));
        company.setTaxId(requestedGstin);
        company.setGstin(requestedGstin);
        company.setGstRegistered(gstRegistered);
        company.setCompositionScheme(Boolean.TRUE.equals(request.getCompositionScheme()));
        company.setPanNumber(blankToNull(request.getPanNumber()));
        company.setCinNumber(blankToNull(request.getCinNumber()));
        company.setWebsiteUrl(blankToNull(request.getWebsiteUrl()));
        company.setBankName(blankToNull(request.getBankName()));
        company.setBankAccountName(blankToNull(request.getBankAccountName()));
        company.setBankAccountNumber(blankToNull(request.getBankAccountNumber()));
        company.setBankIfscCode(blankToNull(request.getBankIfscCode()));
        company.setBankBranch(blankToNull(request.getBankBranch()));
        company.setUpiId(blankToNull(request.getUpiId()));
        company.setInvoiceNotes(blankToNull(request.getInvoiceNotes()));
        company.setInvoiceTerms(blankToNull(request.getInvoiceTerms()));
        company.setDatabaseName(blankToNull(request.getDatabaseName()));

        return toSummary(companyRepository.save(company));
    }

    @Transactional
    public CompanySummary uploadLogo(String email, MultipartFile file) {
        Company company = accessControlService.requireOwnerCompany(email);
        return saveImage(company, file, "company-logos", "company-" + company.getId() + "-", true);
    }

    @Transactional
    public CompanySummary uploadSignature(String email, MultipartFile file) {
        Company company = accessControlService.requireOwnerCompany(email);
        return saveImage(company, file, "company-signatures", "signature-" + company.getId() + "-", false);
    }

    @Transactional
    public CompanySummary deleteSignature(String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        deleteFile(company.getSignatureUrl(), "company-signatures");
        company.setSignatureUrl(null);
        return toSummary(companyRepository.save(company));
    }

    private CompanySummary saveImage(Company company, MultipartFile file, String folderName, String prefix, boolean logo) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException((logo ? "Logo" : "Signature") + " file is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!LOGO_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Only JPG, PNG, and WEBP images are allowed");
        }
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String fileName = prefix + UUID.randomUUID() + extension;
        Path targetDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(folderName);
        Path targetFile = targetDir.resolve(fileName).normalize();
        if (!targetFile.startsWith(targetDir)) {
            throw new BadRequestException("Invalid image path");
        }

        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BadRequestException("Unable to upload company image");
        }

        if (logo) {
            deleteFile(company.getLogoUrl(), folderName);
            company.setLogoUrl("/uploads/" + folderName + "/" + fileName);
        } else {
            deleteFile(company.getSignatureUrl(), folderName);
            company.setSignatureUrl("/uploads/" + folderName + "/" + fileName);
        }
        return toSummary(companyRepository.save(company));
    }

    @Transactional
    public CompanySummary deleteLogo(String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        deleteFile(company.getLogoUrl(), "company-logos");
        company.setLogoUrl(null);
        return toSummary(companyRepository.save(company));
    }

    private void deleteFile(String fileUrl, String folderName) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            Path targetDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(folderName);
            Path fileName = Paths.get(fileUrl).getFileName();
            if (fileName == null) {
                return;
            }
            Path logoFile = targetDir.resolve(fileName.toString()).normalize();
            if (!logoFile.startsWith(targetDir)) {
                return;
            }
            Files.deleteIfExists(logoFile);
        } catch (IOException ignored) {
        }
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
                .stateId(company.getStateMaster() != null ? company.getStateMaster().getId() : null)
                .country(company.getCountry())
                .pincode(company.getPincode())
                .taxId(firstNonBlank(company.getGstin(), company.getTaxId()))
                .gstin(firstNonBlank(company.getGstin(), company.getTaxId()))
                .gstRegistered(company.isGstRegistered())
                .compositionScheme(company.isCompositionScheme())
                .panNumber(company.getPanNumber())
                .cinNumber(company.getCinNumber())
                .logoUrl(company.getLogoUrl())
                .websiteUrl(company.getWebsiteUrl())
                .bankName(company.getBankName())
                .bankAccountName(company.getBankAccountName())
                .bankAccountNumber(company.getBankAccountNumber())
                .bankIfscCode(company.getBankIfscCode())
                .bankBranch(company.getBankBranch())
                .upiId(company.getUpiId())
                .signatureUrl(company.getSignatureUrl())
                .invoiceNotes(company.getInvoiceNotes())
                .invoiceTerms(company.getInvoiceTerms())
                .chatbotEnabled(company.isChatbotEnabled())
                .inventoryConsumptionMethod(company.getInventoryConsumptionMethod().name())
                .inventoryPricingPolicy(company.getInventoryPricingPolicy().name())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = blankToNull(preferred);
        return normalized == null ? blankToNull(fallback) : normalized;
    }

    private CompanyThemeResponse toThemeResponse(CompanyThemeSetting setting) {
        return CompanyThemeResponse.builder()
                .themeColor(setting.getThemeColor())
                .build();
    }
}
