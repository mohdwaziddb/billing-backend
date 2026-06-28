package com.billing.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Long stateId;
    private String country;
    private String pincode;
    private String taxId;
    private String gstin;
    private boolean gstRegistered;
    private boolean compositionScheme;
    private String panNumber;
    private String cinNumber;
    private String logoUrl;
    private String websiteUrl;
    private String bankName;
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankIfscCode;
    private String bankBranch;
    private String upiId;
    private String signatureUrl;
    private String invoiceNotes;
    private String invoiceTerms;
    @JsonProperty("isChatbotEnabled")
    private boolean chatbotEnabled;
    private String inventoryConsumptionMethod;
    private String inventoryPricingPolicy;
}
