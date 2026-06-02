package com.billing.saas.service;

import com.billing.saas.dto.user.CompanySummary;
import com.billing.saas.dto.user.UserProfileResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
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
                .email(company.getEmail())
                .phone(company.getPhone())
                .address(company.getAddress())
                .taxId(company.getTaxId())
                .build();
    }
}
