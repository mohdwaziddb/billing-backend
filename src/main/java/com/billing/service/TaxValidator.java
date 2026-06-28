package com.billing.service;

import com.billing.dto.tax.TaxMasterRequest;
import com.billing.entity.Company;
import com.billing.entity.TaxMaster;
import com.billing.entity.enums.TaxType;
import com.billing.exception.BadRequestException;
import com.billing.repository.TaxMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class TaxValidator {

    private final TaxMasterRepository taxMasterRepository;

    public void validateForCreate(Company company, TaxMasterRequest request) {
        String taxName = normalizeRequired(request.getTaxName(), "Tax name");
        String taxCode = normalizeRequired(request.getTaxCode(), "Tax code");
        validateRate(request.getRate());
        if (taxMasterRepository.existsByCompanyAndTaxNameIgnoreCaseAndDeletedFalse(company, taxName)) {
            throw new BadRequestException("Tax name already exists in your company");
        }
        if (taxMasterRepository.existsByCompanyAndTaxCodeIgnoreCaseAndDeletedFalse(company, taxCode)) {
            throw new BadRequestException("Tax code already exists in your company");
        }
        parseTaxType(request.getTaxType());
    }

    public void validateForUpdate(Company company, Long taxId, TaxMasterRequest request) {
        String taxName = normalizeRequired(request.getTaxName(), "Tax name");
        String taxCode = normalizeRequired(request.getTaxCode(), "Tax code");
        validateRate(request.getRate());
        if (taxMasterRepository.existsByCompanyAndTaxNameIgnoreCaseAndDeletedFalseAndIdNot(company, taxName, taxId)) {
            throw new BadRequestException("Tax name already exists in your company");
        }
        if (taxMasterRepository.existsByCompanyAndTaxCodeIgnoreCaseAndDeletedFalseAndIdNot(company, taxCode, taxId)) {
            throw new BadRequestException("Tax code already exists in your company");
        }
        parseTaxType(request.getTaxType());
    }

    public TaxType parseTaxType(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Tax type is required");
        }
        try {
            return TaxType.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unsupported tax type");
        }
    }

    public BigDecimal normalizeRate(BigDecimal value) {
        validateRate(value);
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required");
        }
        return value.trim();
    }

    public void validateRate(BigDecimal value) {
        if (value == null) {
            throw new BadRequestException("Tax rate is required");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) < 0 || normalized.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Tax rate must be between 0 and 100");
        }
    }

    public TaxMaster requireProductTax(TaxMaster taxMaster, boolean taxable) {
        if (!taxable) {
            return taxMaster;
        }
        if (taxMaster == null) {
            throw new BadRequestException("Select a valid tax");
        }
        if (!taxMaster.isActive() || taxMaster.isDeleted()) {
            throw new BadRequestException("Select an active tax");
        }
        return taxMaster;
    }
}
