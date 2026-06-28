package com.billing.service;

import com.billing.dto.tax.TaxMasterResponse;
import com.billing.entity.TaxMaster;
import org.springframework.stereotype.Component;

@Component
public class TaxMapper {

    public TaxMasterResponse toResponse(TaxMaster taxMaster, AuditNameResolver auditNameResolver) {
        return TaxMasterResponse.builder()
                .id(taxMaster.getId())
                .taxName(taxMaster.getTaxName())
                .taxCode(taxMaster.getTaxCode())
                .taxType(taxMaster.getTaxType().name())
                .rate(taxMaster.getRate())
                .description(taxMaster.getDescription())
                .defaultTax(taxMaster.isDefaultTax())
                .active(taxMaster.isActive())
                .deleted(taxMaster.isDeleted())
                .createdAt(taxMaster.getCreatedAt())
                .updatedAt(taxMaster.getUpdatedAt())
                .createdBy(auditNameResolver.displayName(taxMaster.getCreatedBy()))
                .updatedBy(auditNameResolver.displayName(taxMaster.getUpdatedBy()))
                .build();
    }
}
