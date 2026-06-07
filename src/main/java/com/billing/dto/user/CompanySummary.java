package com.billing.dto.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanySummary {
    private Long id;
    private String name;
    private String legalName;
    private String code;
    private String databaseName;
    private String email;
    private String phone;
    private String alternatePhone;
    private String address;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private String taxId;
    private String panNumber;
    private String cinNumber;
    private String logoUrl;
    private String websiteUrl;
}
