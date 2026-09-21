package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.tax.TaxMasterRequest;
import com.billing.dto.tax.TaxMasterResponse;
import com.billing.entity.Company;
import com.billing.entity.TaxMaster;
import com.billing.entity.enums.TaxType;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.TaxMasterRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaxMasterService {

    private final TaxMasterRepository taxMasterRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;
    private final TaxValidator taxValidator;
    private final TaxMapper taxMapper;

    @Transactional(readOnly = true)
    public PageResponse<TaxMasterResponse> page(String email, String search, Boolean active, String taxType, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        TaxType resolvedTaxType = blankToNull(taxType) == null ? null : taxValidator.parseTaxType(taxType);
        return PageResponse.from(taxMasterRepository.findPageByCompanyWithFilters(company, active, resolvedTaxType, normalizeSearch(search), pageRequest(page, size))
                .map(item -> taxMapper.toResponse(item, auditNameResolver)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxMasterResponse> page(Map<String, Object> param, String email) {
        String searchFilter = DataTypeUtility.stringValue(param.get("search"));
        if (searchFilter.length() == 0) {
            searchFilter = null;
        }
        Boolean activeStatus = null;
        Object activeObject = param.get("active");
        if (activeObject != null) {
            if (activeObject instanceof Boolean) {
                activeStatus = (Boolean) activeObject;
            } else {
                String activeString = DataTypeUtility.stringValue(activeObject);
                if (activeString.length() > 0) {
                    activeStatus = DataTypeUtility.booleanValue(activeString);
                }
            }
        }
        String taxTypeFilter = DataTypeUtility.stringValue(param.get("taxType"));
        if (taxTypeFilter.length() == 0) {
            taxTypeFilter = DataTypeUtility.stringValue(param.get("tax_type"));
        }
        if (taxTypeFilter.length() == 0) {
            taxTypeFilter = null;
        }
        int pageNumber = DataTypeUtility.integerValue(param.get("page"));
        int pageSize = DataTypeUtility.integerValue(param.get("size"));
        if (pageSize == 0) {
            pageSize = 20;
        }
        return page(email, searchFilter, activeStatus, taxTypeFilter, pageNumber, pageSize);
    }

    @Transactional(readOnly = true)
    public List<TaxMasterResponse> list(String email, String search, Boolean active, String taxType) {
        Company company = accessControlService.getCurrentCompany(email);
        TaxType resolvedTaxType = blankToNull(taxType) == null ? null : taxValidator.parseTaxType(taxType);
        return taxMasterRepository.findAllByCompanyWithFilters(company, active, resolvedTaxType, normalizeSearch(search)).stream()
                .map(item -> taxMapper.toResponse(item, auditNameResolver))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxMasterResponse> list(Map<String, Object> param, String email) {
        String searchFilter = DataTypeUtility.stringValue(param.get("search"));
        if (searchFilter.length() == 0) {
            searchFilter = null;
        }
        Boolean activeStatus = null;
        Object activeObject = param.get("active");
        if (activeObject != null) {
            if (activeObject instanceof Boolean) {
                activeStatus = (Boolean) activeObject;
            } else {
                String activeString = DataTypeUtility.stringValue(activeObject);
                if (activeString.length() > 0) {
                    activeStatus = DataTypeUtility.booleanValue(activeString);
                }
            }
        }
        String taxTypeFilter = DataTypeUtility.stringValue(param.get("taxType"));
        if (taxTypeFilter.length() == 0) {
            taxTypeFilter = DataTypeUtility.stringValue(param.get("tax_type"));
        }
        if (taxTypeFilter.length() == 0) {
            taxTypeFilter = null;
        }
        return list(email, searchFilter, activeStatus, taxTypeFilter);
    }

    @Transactional(readOnly = true)
    public TaxMasterResponse get(String email, Long taxId) {
        Company company = accessControlService.getCurrentCompany(email);
        return taxMapper.toResponse(getTaxOrThrow(company, taxId), auditNameResolver);
    }

    @Transactional
    public TaxMasterResponse create(String email, TaxMasterRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        taxValidator.validateForCreate(company, request);

        TaxMaster taxMaster = TaxMaster.builder()
                .company(company)
                .taxName(taxValidator.normalizeRequired(request.getTaxName(), "Tax name"))
                .taxCode(taxValidator.normalizeRequired(request.getTaxCode(), "Tax code"))
                .taxType(taxValidator.parseTaxType(request.getTaxType()))
                .rate(taxValidator.normalizeRate(request.getRate()))
                .description(blankToNull(request.getDescription()))
                .defaultTax(Boolean.TRUE.equals(request.getDefaultTax()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .deleted(false)
                .build();

        if (taxMaster.isDefaultTax()) {
            clearDefaultFlag(company);
        }

        TaxMaster saved = taxMasterRepository.save(taxMaster);
        auditLogService.logCreate(email, company, "Tax Master", "TaxMaster", saved.getId(), snapshot(saved));
        return taxMapper.toResponse(saved, auditNameResolver);
    }

    @Transactional
    public TaxMasterResponse create(Map<String, Object> param, String email) {
        TaxMasterRequest taxMasterRequest = mapToTaxMasterRequest(param);
        return create(email, taxMasterRequest);
    }

    @Transactional
    public TaxMasterResponse update(String email, Long taxId, TaxMasterRequest request) {
        Company company = accessControlService.requireOwnerCompany(email);
        TaxMaster taxMaster = getTaxOrThrow(company, taxId);
        Map<String, Object> oldData = snapshot(taxMaster);
        taxValidator.validateForUpdate(company, taxId, request);

        if (Boolean.TRUE.equals(request.getDefaultTax())) {
            clearDefaultFlag(company);
        }

        taxMaster.setTaxName(taxValidator.normalizeRequired(request.getTaxName(), "Tax name"));
        taxMaster.setTaxCode(taxValidator.normalizeRequired(request.getTaxCode(), "Tax code"));
        taxMaster.setTaxType(taxValidator.parseTaxType(request.getTaxType()));
        taxMaster.setRate(taxValidator.normalizeRate(request.getRate()));
        taxMaster.setDescription(blankToNull(request.getDescription()));
        taxMaster.setDefaultTax(Boolean.TRUE.equals(request.getDefaultTax()));
        taxMaster.setActive(Boolean.TRUE.equals(request.getActive()));

        TaxMaster saved = taxMasterRepository.save(taxMaster);
        auditLogService.logUpdate(email, company, "Tax Master", "TaxMaster", saved.getId(), oldData, snapshot(saved));
        if (oldData.get("active") instanceof Boolean oldActive && oldActive != saved.isActive()) {
            auditLogService.logEvent(email, company, "Tax Master", "TaxMaster", saved.getId(),
                    saved.isActive() ? "ACTIVATE" : "DEACTIVATE", snapshot(saved));
        }
        return taxMapper.toResponse(saved, auditNameResolver);
    }

    @Transactional
    public TaxMasterResponse update(Map<String, Object> param, Long taxId, String email) {
        Long taxIdValue = DataTypeUtility.getForeignKeyValue(taxId);
        if (taxIdValue == null) {
            taxIdValue = DataTypeUtility.getForeignKeyValue(param.get("taxId"));
        }
        if (taxIdValue == null) {
            taxIdValue = DataTypeUtility.getForeignKeyValue(param.get("tax_id"));
        }
        TaxMasterRequest taxMasterRequest = mapToTaxMasterRequest(param);
        return update(email, taxIdValue, taxMasterRequest);
    }

    @Transactional
    public TaxMasterResponse update(Map<String, Object> param, String email) {
        Long taxIdValue = DataTypeUtility.getForeignKeyValue(param.get("taxId"));
        if (taxIdValue == null) {
            taxIdValue = DataTypeUtility.getForeignKeyValue(param.get("tax_id"));
        }
        if (taxIdValue == null) {
            taxIdValue = DataTypeUtility.getForeignKeyValue(param.get("id"));
        }
        TaxMasterRequest taxMasterRequest = mapToTaxMasterRequest(param);
        return update(email, taxIdValue, taxMasterRequest);
    }

    @Transactional
    public void delete(String email, Long taxId) {
        Company company = accessControlService.requireOwnerCompany(email);
        TaxMaster taxMaster = getTaxOrThrow(company, taxId);
        Map<String, Object> oldData = snapshot(taxMaster);
        taxMaster.setActive(false);
        taxMaster.setDeleted(true);
        taxMaster.setDefaultTax(false);
        taxMasterRepository.save(taxMaster);
        auditLogService.logDelete(email, company, "Tax Master", "TaxMaster", taxMaster.getId(), oldData);
    }

    @Transactional
    public void createDefaultTaxesForCompany(Company company) {
        ensureTax(company, BigDecimal.ZERO, true);
        ensureTax(company, BigDecimal.valueOf(5), false);
        ensureTax(company, BigDecimal.valueOf(12), false);
        ensureTax(company, BigDecimal.valueOf(18), false);
        ensureTax(company, BigDecimal.valueOf(28), false);
    }

    @Transactional
    public TaxMaster ensureGstTaxForRate(Company company, BigDecimal rate, boolean defaultTax) {
        BigDecimal normalizedRate = rate == null ? BigDecimal.ZERO : rate.setScale(2, RoundingMode.HALF_UP);
        return taxMasterRepository.findByCompanyAndTaxTypeAndRateAndDeletedFalse(company, TaxType.GST, normalizedRate)
                .orElseGet(() -> ensureTax(company, normalizedRate, defaultTax));
    }

    @Transactional(readOnly = true)
    public TaxMaster getTaxOrThrow(Company company, Long taxId) {
        return taxMasterRepository.findByIdAndCompanyAndDeletedFalse(taxId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Tax master not found"));
    }

    @Transactional
    public TaxMaster resolveForProduct(Company company, Long taxMasterId, BigDecimal legacyTaxPercent, boolean taxable) {
        if (!taxable) {
            return ensureGstTaxForRate(company, BigDecimal.ZERO, true);
        }
        if (taxMasterId != null) {
            return getTaxOrThrow(company, taxMasterId);
        }
        if (legacyTaxPercent != null) {
            return ensureGstTaxForRate(company, legacyTaxPercent, false);
        }
        return taxMasterRepository.findByCompanyAndDefaultTaxTrueAndDeletedFalse(company)
                .orElseThrow(() -> new BadRequestException("Select a tax for this product"));
    }

    private TaxMasterRequest mapToTaxMasterRequest(Map<String, Object> param) {
        TaxMasterRequest taxMasterRequest = new TaxMasterRequest();
        String taxNameValue = DataTypeUtility.stringValue(param.get("taxName"));
        if (taxNameValue.length() == 0) {
            taxNameValue = DataTypeUtility.stringValue(param.get("tax_name"));
        }
        taxMasterRequest.setTaxName(taxNameValue);
        String taxCodeValue = DataTypeUtility.stringValue(param.get("taxCode"));
        if (taxCodeValue.length() == 0) {
            taxCodeValue = DataTypeUtility.stringValue(param.get("tax_code"));
        }
        taxMasterRequest.setTaxCode(taxCodeValue);
        String taxTypeValue = DataTypeUtility.stringValue(param.get("taxType"));
        if (taxTypeValue.length() == 0) {
            taxTypeValue = DataTypeUtility.stringValue(param.get("tax_type"));
        }
        taxMasterRequest.setTaxType(taxTypeValue);
        BigDecimal rateValue = DataTypeUtility.bigDecimalObjectValue(param.get("rate"));
        if (rateValue == null) {
            rateValue = DataTypeUtility.bigDecimalObjectValue(param.get("taxRate"));
        }
        taxMasterRequest.setRate(rateValue);
        String descriptionValue = DataTypeUtility.stringValue(param.get("description"));
        if (descriptionValue.length() == 0) {
            descriptionValue = null;
        }
        taxMasterRequest.setDescription(descriptionValue);
        Object defaultTaxObject = param.get("defaultTax");
        if (defaultTaxObject == null) {
            defaultTaxObject = param.get("default_tax");
        }
        if (defaultTaxObject != null) {
            if (defaultTaxObject instanceof Boolean) {
                taxMasterRequest.setDefaultTax((Boolean) defaultTaxObject);
            } else {
                String defaultTaxString = DataTypeUtility.stringValue(defaultTaxObject);
                if (defaultTaxString.length() > 0) {
                    taxMasterRequest.setDefaultTax(DataTypeUtility.booleanValue(defaultTaxString));
                } else {
                    taxMasterRequest.setDefaultTax(false);
                }
            }
        } else {
            taxMasterRequest.setDefaultTax(false);
        }
        Object activeObject = param.get("active");
        if (activeObject != null) {
            if (activeObject instanceof Boolean) {
                taxMasterRequest.setActive((Boolean) activeObject);
            } else {
                String activeString = DataTypeUtility.stringValue(activeObject);
                if (activeString.length() > 0) {
                    taxMasterRequest.setActive(DataTypeUtility.booleanValue(activeString));
                } else {
                    taxMasterRequest.setActive(false);
                }
            }
        } else {
            taxMasterRequest.setActive(false);
        }
        return taxMasterRequest;
    }

    private TaxMaster ensureTax(Company company, BigDecimal rate, boolean defaultTax) {
        BigDecimal normalizedRate = rate.setScale(2, RoundingMode.HALF_UP);
        TaxMaster existing = taxMasterRepository.findByCompanyAndTaxTypeAndRateAndDeletedFalse(company, TaxType.GST, normalizedRate).orElse(null);
        if (existing != null) {
            if (defaultTax && !existing.isDefaultTax()) {
                clearDefaultFlag(company);
                existing.setDefaultTax(true);
                taxMasterRepository.save(existing);
            }
            return existing;
        }
        if (defaultTax) {
            clearDefaultFlag(company);
        }
        return taxMasterRepository.save(TaxMaster.builder()
                .company(company)
                .taxName("GST " + displayRate(normalizedRate) + "%")
                .taxCode("GST_" + displayRate(normalizedRate).replace('.', '_'))
                .taxType(TaxType.GST)
                .rate(normalizedRate)
                .description("Default GST " + displayRate(normalizedRate) + "%")
                .defaultTax(defaultTax)
                .active(true)
                .deleted(false)
                .build());
    }

    private void clearDefaultFlag(Company company) {
        taxMasterRepository.findByCompanyAndDefaultTaxTrueAndDeletedFalse(company).ifPresent(existing -> {
            existing.setDefaultTax(false);
            taxMasterRepository.save(existing);
        });
    }

    private String displayRate(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> snapshot(TaxMaster taxMaster) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taxName", taxMaster.getTaxName());
        data.put("taxCode", taxMaster.getTaxCode());
        data.put("taxType", taxMaster.getTaxType().name());
        data.put("rate", taxMaster.getRate());
        data.put("description", taxMaster.getDescription());
        data.put("defaultTax", taxMaster.isDefaultTax());
        data.put("active", taxMaster.isActive());
        data.put("deleted", taxMaster.isDeleted());
        return data;
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }
}
