package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.dto.user.CompanySummary;
import com.billing.dto.user.UserProfileResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .mobileNumber(user.getMobileNumber())
                .email(user.getEmail())
                .role(user.getRole().name())
                .active(user.isActive())
                .company(toCompanySummary(user.getCompany()))
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
                .logoUrl(company.getLogoUrl())
                .websiteUrl(company.getWebsiteUrl())
                .build();
    }
}
