package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.dto.user.CompanySummary;
import com.billing.dto.user.UserProfileResponse;
import com.billing.entity.enums.RoleName;
import com.billing.util.CompanyFileJson;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toProfile(User user) {
        return toProfile(user, user.getCompany());
    }

    public UserProfileResponse toProfile(User user, Company company) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .mobileNumber(user.getMobileNumber())
                .email(user.getEmail())
                .username(user.getUsername())
                .role((user.getRole() == null ? RoleName.USER : user.getRole()).name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .company(toCompanySummary(company))
                .superAdmin(user.isSuperAdmin())
                .photoUrl(CompanyFileJson.extractUrl(user.getPhotoUrl()))
                .photoFile(CompanyFileJson.parse(user.getPhotoUrl()))
                .build();
    }

    private CompanySummary toCompanySummary(Company company) {
        if (company == null) {
            return null;
        }

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
                .logoUrl(CompanyFileJson.extractUrl(company.getLogoUrl()))
                .logoFile(CompanyFileJson.parse(company.getLogoUrl()))
                .websiteUrl(company.getWebsiteUrl())
                .chatbotEnabled(company.isChatbotEnabled())
                .applyMfa(company.isApplyMfa())
                .inventoryConsumptionMethod(company.getInventoryConsumptionMethod().name())
                .inventoryPricingPolicy(company.getInventoryPricingPolicy().name())
                .build();
    }
}
