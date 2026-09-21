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
import com.billing.util.DataTypeUtility;
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
import java.util.Map;
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
    public CompanySummary getSettings(Map<String, Object> param, String email) {
        if (param == null) {
            param = new java.util.HashMap<>();
        }
        return getSettings(email);
    }

    @Transactional(readOnly = true)
    public CompanySummary getSettings(String email) {
        return toSummary(accessControlService.getCurrentCompany(email));
    }

    @Transactional
    public CompanySummary updateSettings(Map<String, Object> param, String email) {
        CompanySettingsRequest companySettingsRequest = new CompanySettingsRequest();
        String companyName = DataTypeUtility.stringValue(param.get("name"));
        if (companyName.length() == 0) {
            companyName = DataTypeUtility.stringValue(param.get("company_name"));
        }
        companySettingsRequest.setName(companyName);
        String legalNameValue = DataTypeUtility.stringValue(param.get("legalName"));
        if (legalNameValue.length() == 0) {
            legalNameValue = DataTypeUtility.stringValue(param.get("legal_name"));
        }
        if (legalNameValue.length() == 0) {
            legalNameValue = null;
        }
        companySettingsRequest.setLegalName(legalNameValue);
        String emailValue = DataTypeUtility.stringValue(param.get("email"));
        companySettingsRequest.setEmail(emailValue);
        String phoneValue = DataTypeUtility.stringValue(param.get("phone"));
        companySettingsRequest.setPhone(phoneValue);
        String alternatePhoneValue = DataTypeUtility.stringValue(param.get("alternatePhone"));
        if (alternatePhoneValue.length() == 0) {
            alternatePhoneValue = DataTypeUtility.stringValue(param.get("alternate_phone"));
        }
        if (alternatePhoneValue.length() == 0) {
            alternatePhoneValue = null;
        }
        companySettingsRequest.setAlternatePhone(alternatePhoneValue);
        String addressValue = DataTypeUtility.stringValue(param.get("address"));
        if (addressValue.length() == 0) {
            addressValue = DataTypeUtility.stringValue(param.get("addressLine1"));
        }
        if (addressValue.length() == 0) {
            addressValue = DataTypeUtility.stringValue(param.get("address_line1"));
        }
        companySettingsRequest.setAddress(addressValue);
        String addressLine1Value = DataTypeUtility.stringValue(param.get("addressLine1"));
        if (addressLine1Value.length() == 0) {
            addressLine1Value = DataTypeUtility.stringValue(param.get("address_line1"));
        }
        if (addressLine1Value.length() == 0) {
            addressLine1Value = null;
        }
        companySettingsRequest.setAddressLine1(addressLine1Value);
        String addressLine2Value = DataTypeUtility.stringValue(param.get("addressLine2"));
        if (addressLine2Value.length() == 0) {
            addressLine2Value = DataTypeUtility.stringValue(param.get("address_line2"));
        }
        if (addressLine2Value.length() == 0) {
            addressLine2Value = null;
        }
        companySettingsRequest.setAddressLine2(addressLine2Value);
        String cityValue = DataTypeUtility.stringValue(param.get("city"));
        if (cityValue.length() == 0) {
            cityValue = null;
        }
        companySettingsRequest.setCity(cityValue);
        String stateValue = DataTypeUtility.stringValue(param.get("state"));
        if (stateValue.length() == 0) {
            stateValue = null;
        }
        companySettingsRequest.setState(stateValue);
        Long stateIdValue = DataTypeUtility.getForeignKeyValue(param.get("stateId"));
        if (stateIdValue == null) {
            stateIdValue = DataTypeUtility.getForeignKeyValue(param.get("state_id"));
        }
        companySettingsRequest.setStateId(stateIdValue);
        String countryValue = DataTypeUtility.stringValue(param.get("country"));
        if (countryValue.length() == 0) {
            countryValue = null;
        }
        companySettingsRequest.setCountry(countryValue);
        String pincodeValue = DataTypeUtility.stringValue(param.get("pincode"));
        if (pincodeValue.length() == 0) {
            pincodeValue = null;
        }
        companySettingsRequest.setPincode(pincodeValue);
        String taxIdValue = DataTypeUtility.stringValue(param.get("taxId"));
        if (taxIdValue.length() == 0) {
            taxIdValue = DataTypeUtility.stringValue(param.get("tax_id"));
        }
        if (taxIdValue.length() == 0) {
            taxIdValue = DataTypeUtility.stringValue(param.get("gstin"));
        }
        if (taxIdValue.length() == 0) {
            taxIdValue = DataTypeUtility.stringValue(param.get("gstNo"));
        }
        if (taxIdValue.length() == 0) {
            taxIdValue = null;
        }
        companySettingsRequest.setTaxId(taxIdValue);
        companySettingsRequest.setGstin(taxIdValue);
        boolean gstRegisteredValue = DataTypeUtility.booleanValue(param.get("gstRegistered"));
        if (!gstRegisteredValue) {
            gstRegisteredValue = DataTypeUtility.booleanValue(param.get("gst_registered"));
        }
        companySettingsRequest.setGstRegistered(gstRegisteredValue);
        boolean compositionSchemeValue = DataTypeUtility.booleanValue(param.get("compositionScheme"));
        if (!compositionSchemeValue) {
            compositionSchemeValue = DataTypeUtility.booleanValue(param.get("composition_scheme"));
        }
        companySettingsRequest.setCompositionScheme(compositionSchemeValue);
        String panNumberValue = DataTypeUtility.stringValue(param.get("panNumber"));
        if (panNumberValue.length() == 0) {
            panNumberValue = DataTypeUtility.stringValue(param.get("pan_number"));
        }
        if (panNumberValue.length() == 0) {
            panNumberValue = null;
        }
        companySettingsRequest.setPanNumber(panNumberValue);
        String cinNumberValue = DataTypeUtility.stringValue(param.get("cinNumber"));
        if (cinNumberValue.length() == 0) {
            cinNumberValue = DataTypeUtility.stringValue(param.get("cin_number"));
        }
        if (cinNumberValue.length() == 0) {
            cinNumberValue = null;
        }
        companySettingsRequest.setCinNumber(cinNumberValue);
        String websiteUrlValue = DataTypeUtility.stringValue(param.get("websiteUrl"));
        if (websiteUrlValue.length() == 0) {
            websiteUrlValue = DataTypeUtility.stringValue(param.get("website_url"));
        }
        if (websiteUrlValue.length() == 0) {
            websiteUrlValue = null;
        }
        companySettingsRequest.setWebsiteUrl(websiteUrlValue);
        String databaseNameValue = DataTypeUtility.stringValue(param.get("databaseName"));
        if (databaseNameValue.length() == 0) {
            databaseNameValue = DataTypeUtility.stringValue(param.get("database_name"));
        }
        if (databaseNameValue.length() == 0) {
            databaseNameValue = null;
        }
        companySettingsRequest.setDatabaseName(databaseNameValue);
        String bankNameValue = DataTypeUtility.stringValue(param.get("bankName"));
        if (bankNameValue.length() == 0) {
            bankNameValue = DataTypeUtility.stringValue(param.get("bank_name"));
        }
        if (bankNameValue.length() == 0) {
            bankNameValue = null;
        }
        companySettingsRequest.setBankName(bankNameValue);
        String bankAccountNameValue = DataTypeUtility.stringValue(param.get("bankAccountName"));
        if (bankAccountNameValue.length() == 0) {
            bankAccountNameValue = DataTypeUtility.stringValue(param.get("bank_account_name"));
        }
        if (bankAccountNameValue.length() == 0) {
            bankAccountNameValue = null;
        }
        companySettingsRequest.setBankAccountName(bankAccountNameValue);
        String bankAccountNumberValue = DataTypeUtility.stringValue(param.get("bankAccountNumber"));
        if (bankAccountNumberValue.length() == 0) {
            bankAccountNumberValue = DataTypeUtility.stringValue(param.get("bank_account_number"));
        }
        if (bankAccountNumberValue.length() == 0) {
            bankAccountNumberValue = null;
        }
        companySettingsRequest.setBankAccountNumber(bankAccountNumberValue);
        String bankIfscCodeValue = DataTypeUtility.stringValue(param.get("bankIfscCode"));
        if (bankIfscCodeValue.length() == 0) {
            bankIfscCodeValue = DataTypeUtility.stringValue(param.get("bank_ifsc_code"));
        }
        if (bankIfscCodeValue.length() == 0) {
            bankIfscCodeValue = null;
        }
        companySettingsRequest.setBankIfscCode(bankIfscCodeValue);
        String bankBranchValue = DataTypeUtility.stringValue(param.get("bankBranch"));
        if (bankBranchValue.length() == 0) {
            bankBranchValue = DataTypeUtility.stringValue(param.get("bank_branch"));
        }
        if (bankBranchValue.length() == 0) {
            bankBranchValue = null;
        }
        companySettingsRequest.setBankBranch(bankBranchValue);
        String upiIdValue = DataTypeUtility.stringValue(param.get("upiId"));
        if (upiIdValue.length() == 0) {
            upiIdValue = DataTypeUtility.stringValue(param.get("upi_id"));
        }
        if (upiIdValue.length() == 0) {
            upiIdValue = null;
        }
        companySettingsRequest.setUpiId(upiIdValue);
        String invoiceNotesValue = DataTypeUtility.stringValue(param.get("invoiceNotes"));
        if (invoiceNotesValue.length() == 0) {
            invoiceNotesValue = DataTypeUtility.stringValue(param.get("invoice_notes"));
        }
        if (invoiceNotesValue.length() == 0) {
            invoiceNotesValue = null;
        }
        companySettingsRequest.setInvoiceNotes(invoiceNotesValue);
        String invoiceTermsValue = DataTypeUtility.stringValue(param.get("invoiceTerms"));
        if (invoiceTermsValue.length() == 0) {
            invoiceTermsValue = DataTypeUtility.stringValue(param.get("invoice_terms"));
        }
        if (invoiceTermsValue.length() == 0) {
            invoiceTermsValue = null;
        }
        companySettingsRequest.setInvoiceTerms(invoiceTermsValue);
        return updateSettings(email, companySettingsRequest);
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
    public CompanyThemeResponse theme(Map<String, Object> param, String email) {
        if (param == null) {
            param = new java.util.HashMap<>();
        }
        return theme(email);
    }

    @Transactional(readOnly = true)
    public CompanyThemeResponse theme(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        CompanyThemeSetting setting = companyThemeSettingRepository.findByCompany(company)
                .orElseGet(() -> CompanyThemeSetting.builder().company(company).themeColor("#0EA5E9").build());
        return toThemeResponse(setting);
    }

    @Transactional
    public CompanyThemeResponse updateTheme(Map<String, Object> param, String email) {
        String themeColorValue = DataTypeUtility.stringValue(param.get("themeColor"));
        if (themeColorValue.length() == 0) {
            themeColorValue = DataTypeUtility.stringValue(param.get("theme_color"));
        }
        CompanyThemeRequest companyThemeRequest = new CompanyThemeRequest();
        companyThemeRequest.setThemeColor(themeColorValue);
        return updateTheme(email, companyThemeRequest);
    }

    @Transactional
    public CompanyThemeResponse resetTheme(Map<String, Object> param, String email) {
        if (param == null) {
            param = new java.util.HashMap<>();
        }
        return resetTheme(email);
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
                .inventoryConsumptionMethod(company.getInventoryConsumptionMethod().name())
                .inventoryPricingPolicy(company.getInventoryPricingPolicy().name())
                .chatbotEnabled(company.isChatbotEnabled())
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
