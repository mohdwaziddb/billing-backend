package com.billing.saas.dto.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanySummary {
    private Long id;
    private String name;
    private String code;
    private String databaseName;
    private String email;
    private String phone;
    private String address;
    private String taxId;
}
