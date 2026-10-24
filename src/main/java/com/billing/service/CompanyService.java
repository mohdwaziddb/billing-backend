package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.CompanyThemeSetting;
import com.billing.entity.enums.InventoryConsumptionMethod;
import com.billing.entity.enums.InventoryPricingPolicy;
import com.billing.dto.company.CompanyFileInfo;
import com.billing.dto.company.CompanySettingsRequest;
import com.billing.dto.company.CompanyThemeRequest;
import com.billing.dto.company.CompanyThemeResponse;
import com.billing.dto.user.CompanySummary;
import com.billing.exception.BadRequestException;
import com.billing.multitenancy.TenantContextHolder;
import com.billing.repository.CompanyRepository;
import com.billing.repository.CompanyThemeSettingRepository;
import com.billing.service.storage.TenantImageService;
import com.billing.util.CompanyFileJson;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final AccessControlService accessControlService;
    private final CompanyRepository companyRepository;
    private final CompanyThemeSettingRepository companyThemeSettingRepository;
    private final StateMasterService stateMasterService;
    private final AuditLogService auditLogService;
    private final TenantImageService tenantImageService;

    /**
     * Current tenant's company for public surfaces (login page branding).
     * Tenant itself is the truth (set by RequestInterceptor from subdomain/header/domain):
     * works for subdomains, maacreation.localhost and plain localhost:5173 alike.
     * Returns null when the tenant has no company row (e.g. platform marker).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentTenantCompany() {
        String tenant = TenantContextHolder.getTenant();
        if (tenant == null || tenant.isBlank()) {
            return null;
        }
        String db = tenant.replace("_read", "").replace("_write", "").trim();
        if (db.isEmpty() || "billing_common".equalsIgnoreCase(db)) {
            return null;
        }
        try {
            java.util.Optional<Company> companyOpt = companyRepository.findByCodeIgnoreCase(db.toUpperCase(Locale.ROOT));
            if (companyOpt.isEmpty()) {
                // Local single-DB mode only: fall back to the lone companies row so
                // public branding (login page) never goes blank on code/database drift.
                // Live: return null so drift never serves the WRONG company's branding.
                if (!com.billing.core.Registry.IS_ONLINE) {
                    java.util.List<Company> all = companyRepository.findAll();
                    if (!all.isEmpty()) {
                        companyOpt = java.util.Optional.of(all.get(0));
                    }
                }
                if (companyOpt.isEmpty()) {
                    return null;
                }
            }
            Company c = companyOpt.get();
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("code", c.getCode());
            map.put("databaseName", c.getDatabaseName());
            map.put("email", c.getEmail());
            map.put("phone", c.getPhone());
            map.put("logoUrl", CompanyFileJson.extractUrl(c.getLogoUrl()));
            map.put("active", c.isActive());
            // Server-authoritative chatbot flag for the tenant widget: login-time
            // session data goes stale when Platform Admin toggles mid-session.
            map.put("chatbotEnabled", c.isChatbotEnabled());
            return map;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getByDomain(String domain) {
        // Local single-DB mode: every subdomain shows the same (only) company,
        // so return the tenant's single row instead of 404-redirecting.
        if (!com.billing.core.Registry.IS_ONLINE) {
            return getCurrentTenantCompany();
        }
        String cleanDomain = DataTypeUtility.stringValue(domain).toLowerCase().trim();
        if (cleanDomain.isBlank()) {
            return null;
        }
        String subdomain = cleanDomain.split("\\.")[0];
        // Reserved labels are never tenant codes (single shared constants).
        if (subdomain.isBlank() || subdomain.equalsIgnoreCase("www")
                || subdomain.equalsIgnoreCase(com.billing.core.AppDomains.MAIN_DOMAIN.split("\\.")[0])
                || subdomain.equalsIgnoreCase(com.billing.core.AppDomains.LOCALHOST)) {
            return null;
        }
        try {
            java.util.Optional<Company> companyOpt = companyRepository.findByCodeIgnoreCase(subdomain.toUpperCase());
            if (companyOpt.isPresent()) {
                Company c = companyOpt.get();
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                // Public (bina login) endpoint: sirf branding fields. databaseName/id
                // internal hain, inse DB naam pata chalta tha.
                map.put("name", c.getName());
                map.put("code", c.getCode());
                map.put("email", c.getEmail());
                map.put("logoUrl", CompanyFileJson.extractUrl(c.getLogoUrl()));
                map.put("active", c.isActive());
                map.put("chatbotEnabled", c.isChatbotEnabled());
                return map;
            }
        } catch (Exception ignored) {}
        return null;
    }

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
        // SECURITY: databaseName is immutable via public API. It is resolved
        // server-side from the tenant context (JWT + registry). Accepting it
        // from the client would allow registry/schema desync and tenant confusion.
        companySettingsRequest.setDatabaseName(null);
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
        String inventoryConsumptionMethodValue = DataTypeUtility.stringValue(param.get("inventoryConsumptionMethod"));
        if (inventoryConsumptionMethodValue.length() == 0) {
            inventoryConsumptionMethodValue = DataTypeUtility.stringValue(param.get("inventory_consumption_method"));
        }
        if (inventoryConsumptionMethodValue.length() == 0) {
            inventoryConsumptionMethodValue = null;
        }
        companySettingsRequest.setInventoryConsumptionMethod(inventoryConsumptionMethodValue);
        String inventoryPricingPolicyValue = DataTypeUtility.stringValue(param.get("inventoryPricingPolicy"));
        if (inventoryPricingPolicyValue.length() == 0) {
            inventoryPricingPolicyValue = DataTypeUtility.stringValue(param.get("inventory_pricing_policy"));
        }
        if (inventoryPricingPolicyValue.length() == 0) {
            inventoryPricingPolicyValue = null;
        }
        companySettingsRequest.setInventoryPricingPolicy(inventoryPricingPolicyValue);
        return updateSettings(email, companySettingsRequest);
    }

    // About Company form ke rules (controller Map leta hai, isliye DTO annotations nahi chalte — yahin check).
    static final String COMPANY_EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";
    static final String COMPANY_MOBILE_PATTERN = "^\\d{10}$";
    static final String GSTIN_PATTERN = "^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    static final String PAN_PATTERN = "^[A-Z]{5}\\d{4}[A-Z]$";
    static final String IFSC_PATTERN = "^[A-Z]{4}0[A-Z0-9]{6}$";
    static final String PINCODE_PATTERN = "^[1-9]\\d{5}$";

    static void validateSettingsRequest(CompanySettingsRequest request) {
        String companyName = DataTypeUtility.stringValue(request.getName());
        String companyEmail = DataTypeUtility.stringValue(request.getEmail());
        String companyPhone = DataTypeUtility.stringValue(request.getPhone());
        String alternatePhone = DataTypeUtility.stringValue(request.getAlternatePhone());
        String pincode = DataTypeUtility.stringValue(request.getPincode());
        String panNumber = DataTypeUtility.stringValue(request.getPanNumber()).toUpperCase();
        String ifscCode = DataTypeUtility.stringValue(request.getBankIfscCode()).toUpperCase();
        if (companyName.length() == 0) {
            throw new BadRequestException("Company name is required");
        }
        if (companyEmail.length() == 0) {
            throw new BadRequestException("Email address is required");
        }
        if (!companyEmail.matches(COMPANY_EMAIL_PATTERN)) {
            throw new BadRequestException("Enter a valid email address");
        }
        if (companyPhone.length() == 0) {
            throw new BadRequestException("Mobile number is required");
        }
        if (!companyPhone.matches(COMPANY_MOBILE_PATTERN)) {
            throw new BadRequestException("Enter a 10-digit mobile number");
        }
        if (alternatePhone.length() > 0 && !alternatePhone.matches(COMPANY_MOBILE_PATTERN)) {
            throw new BadRequestException("Enter a 10-digit alternate mobile number");
        }
        if (Boolean.TRUE.equals(request.getGstRegistered())) {
            String gstin = DataTypeUtility.stringValue(request.getGstin()).toUpperCase();
            if (gstin.length() == 0) {
                gstin = DataTypeUtility.stringValue(request.getTaxId()).toUpperCase();
            }
            if (gstin.length() > 0 && !gstin.matches(GSTIN_PATTERN)) {
                throw new BadRequestException("Enter a valid 15-character GSTIN (e.g. 07ABCDE1234F1Z5)");
            }
        }
        if (panNumber.length() > 0 && !panNumber.matches(PAN_PATTERN)) {
            throw new BadRequestException("Enter a valid 10-character PAN (e.g. ABCDE1234F)");
        }
        if (ifscCode.length() > 0 && !ifscCode.matches(IFSC_PATTERN)) {
            throw new BadRequestException("Enter a valid 11-character IFSC code (e.g. SBIN0001234)");
        }
        if (pincode.length() > 0 && !pincode.matches(PINCODE_PATTERN)) {
            throw new BadRequestException("Enter a 6-digit pincode");
        }
    }

    @Transactional
    public CompanySummary updateSettings(String email, CompanySettingsRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        validateSettingsRequest(request);
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
        if (requestedGstin != null) {
            requestedGstin = requestedGstin.toUpperCase();
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
        // SECURITY: never update databaseName from client request (see above).

        String consumptionMethodValue = blankToNull(request.getInventoryConsumptionMethod());
        if (consumptionMethodValue != null) {
            InventoryConsumptionMethod consumptionMethod;
            try {
                consumptionMethod = InventoryConsumptionMethod.valueOf(consumptionMethodValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid stock consumption method: " + consumptionMethodValue);
            }
            if (consumptionMethod == InventoryConsumptionMethod.WEIGHTED_AVERAGE) {
                throw new BadRequestException("Stock consumption method WEIGHTED_AVERAGE is not supported yet");
            }
            company.setInventoryConsumptionMethod(consumptionMethod);
        }

        String pricingPolicyValue = blankToNull(request.getInventoryPricingPolicy());
        if (pricingPolicyValue != null) {
            InventoryPricingPolicy pricingPolicy;
            try {
                pricingPolicy = InventoryPricingPolicy.valueOf(pricingPolicyValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid default selling price policy: " + pricingPolicyValue);
            }
            if (pricingPolicy == InventoryPricingPolicy.PRODUCT_MASTER_SELLING_PRICE
                    || pricingPolicy == InventoryPricingPolicy.PRICE_LIST) {
                throw new BadRequestException("Default selling price policy " + pricingPolicy.name() + " is not supported yet");
            }
            company.setInventoryPricingPolicy(pricingPolicy);
        }

        return toSummary(companyRepository.save(company));
    }

    @Transactional
    public CompanySummary uploadLogo(String email, MultipartFile file) {
        Company company = accessControlService.requireOwnerCompany(email);
        return saveImage(company, file, "logo", true);
    }

    @Transactional
    public CompanySummary uploadSignature(String email, MultipartFile file) {
        Company company = accessControlService.requireOwnerCompany(email);
        return saveImage(company, file, "signature", false);
    }

    @Transactional
    public CompanySummary deleteSignature(String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        String oldDbValue = company.getSignatureUrl();
        company.setSignatureUrl(null);
        CompanySummary summary = toSummary(companyRepository.save(company));
        tenantImageService.delete(oldDbValue);
        return summary;
    }

    /**
     * Stores the image (flat tenant key: S3, or local disk when S3 is disabled) and
     * saves the FULL file JSON ({@code file_name, type, url, key, size, ...}) into
     * the existing {@code logo_url} / {@code signature_url} column.
     *
     * Same naam dobara aaye to key identical hoti hai (in-place overwrite) — us
     * case me purani delete SKIP hoti hai, warna abhi-upload ki file ud jayegi.
     * Alag naam aaye to purani key delete hoti hai. Delete hamesha successful
     * DB save ke BAAD, taaki failed upload kabhi data orphan na kare.
     */
    private CompanySummary saveImage(Company company, MultipartFile file, String slot, boolean logo) {
        String oldDbValue = logo ? company.getLogoUrl() : company.getSignatureUrl();
        String nextDbValue = tenantImageService.store(company, slot, file, oldDbValue, logo ? "Logo" : "Signature");
        if (logo) {
            company.setLogoUrl(nextDbValue);
        } else {
            company.setSignatureUrl(nextDbValue);
        }
        CompanySummary summary = toSummary(companyRepository.save(company));
        tenantImageService.cleanupReplaced(oldDbValue, nextDbValue);
        return summary;
    }

    @Transactional
    public CompanySummary deleteLogo(String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        String oldDbValue = company.getLogoUrl();
        company.setLogoUrl(null);
        CompanySummary summary = toSummary(companyRepository.save(company));
        tenantImageService.delete(oldDbValue);
        return summary;
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
                .orElseGet(() -> CompanyThemeSetting.builder().company(company).themeColor(com.billing.config.MasterSeedCatalog.DEFAULT_THEME_COLOR).build());
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
        String themeColorValue = DataTypeUtility.stringValue(request.getThemeColor()).trim().toUpperCase(Locale.ROOT);
        if (themeColorValue.length() == 0) {
            throw new BadRequestException("Theme color is required");
        }
        if (!themeColorValue.matches("#[0-9A-F]{6}(,#[0-9A-F]{6})?")) {
            throw new BadRequestException("Theme color must be a HEX color like #0EA5E9, or two colors for a gradient like #0EA5E9,#8B5CF6");
        }
        setting.setThemeColor(themeColorValue);
        return toThemeResponse(companyThemeSettingRepository.save(setting));
    }

    @Transactional
    public CompanyThemeResponse resetTheme(String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        CompanyThemeSetting setting = companyThemeSettingRepository.findByCompany(company)
                .orElseGet(() -> CompanyThemeSetting.builder().company(company).build());
        setting.setThemeColor(com.billing.config.MasterSeedCatalog.DEFAULT_THEME_COLOR);
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
                .logoUrl(CompanyFileJson.extractUrl(company.getLogoUrl()))
                .logoFile(CompanyFileJson.parse(company.getLogoUrl()))
                .websiteUrl(company.getWebsiteUrl())
                .bankName(company.getBankName())
                .bankAccountName(company.getBankAccountName())
                .bankAccountNumber(company.getBankAccountNumber())
                .bankIfscCode(company.getBankIfscCode())
                .bankBranch(company.getBankBranch())
                .upiId(company.getUpiId())
                .signatureUrl(CompanyFileJson.extractUrl(company.getSignatureUrl()))
                .signatureFile(CompanyFileJson.parse(company.getSignatureUrl()))
                .invoiceNotes(company.getInvoiceNotes())
                .invoiceTerms(company.getInvoiceTerms())
                .inventoryConsumptionMethod(company.getInventoryConsumptionMethod().name())
                .inventoryPricingPolicy(company.getInventoryPricingPolicy().name())
                .chatbotEnabled(company.isChatbotEnabled())
                .applyMfa(company.isApplyMfa())
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMfaSettings(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("applyMfa", company.isApplyMfa());
        data.put("companyId", company.getId());
        data.put("companyCode", company.getCode());
        return data;
    }

    @Transactional
    public Map<String, Object> updateApplyMfa(Map<String, Object> param, String email) {
        Company company = accessControlService.requireOwnerCompany(email);
        boolean applyMfa = DataTypeUtility.booleanValue(param != null ? param.get("isApplyMfa") : null);
        if (param != null && param.containsKey("applyMfa") && !param.containsKey("isApplyMfa")) {
            applyMfa = DataTypeUtility.booleanValue(param.get("applyMfa"));
        }
        if (param != null && param.containsKey("is_apply_mfa") && !param.containsKey("isApplyMfa") && !param.containsKey("applyMfa")) {
            applyMfa = DataTypeUtility.booleanValue(param.get("is_apply_mfa"));
        }
        boolean oldValue = company.isApplyMfa();
        company.setApplyMfa(applyMfa);
        companyRepository.save(company);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("applyMfa", company.isApplyMfa());
        data.put("companyId", company.getId());
        if (oldValue != applyMfa) {
            try {
                java.util.Map<String, Object> oldData = Map.of("applyMfa", oldValue);
                auditLogService.logUpdate(email, company, "COMPANY", "companies", company.getId(), oldData, Map.of("applyMfa", applyMfa));
            } catch (Exception ignored) {
                // Audit kabhi toggle block nahi karega
            }
        }
        return data;
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
