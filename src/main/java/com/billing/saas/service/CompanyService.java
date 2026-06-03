package com.billing.saas.service;

import com.billing.saas.dto.company.CompanySettingsRequest;
import com.billing.saas.dto.user.CompanySummary;
import com.billing.saas.entity.Company;
import com.billing.saas.exception.BadRequestException;
import com.billing.saas.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final AccessControlService accessControlService;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public CompanySummary getSettings(String email) {
        return toSummary(accessControlService.getCurrentCompany(email));
    }

    @Transactional
    public CompanySummary updateSettings(String email, CompanySettingsRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        if (!company.getEmail().equalsIgnoreCase(request.getEmail())
                && companyRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("Company email already exists");
        }
        if (!company.getTaxId().equalsIgnoreCase(request.getTaxId())
                && companyRepository.existsByTaxIdIgnoreCase(request.getTaxId())) {
            throw new BadRequestException("Tax ID already exists");
        }

        company.setName(request.getName());
        company.setEmail(request.getEmail());
        company.setPhone(request.getPhone());
        company.setAddress(request.getAddress());
        company.setTaxId(request.getTaxId());
        company.setDatabaseName(blankToNull(request.getDatabaseName()));

        return toSummary(companyRepository.save(company));
    }

    private CompanySummary toSummary(Company company) {
        return CompanySummary.builder()
                .id(company.getId())
                .name(company.getName())
                .code(company.getCode())
                .databaseName(company.getDatabaseName())
                .email(company.getEmail())
                .phone(company.getPhone())
                .address(company.getAddress())
                .taxId(company.getTaxId())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
