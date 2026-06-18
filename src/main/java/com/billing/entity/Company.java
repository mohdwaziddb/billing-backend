package com.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "database_name")
    private String databaseName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "legal_name")
    private String legalName;

    private String phone;

    @Column(name = "alternate_phone")
    private String alternatePhone;

    private String address;

    @Column(name = "address_line_1")
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String pincode;

    @Column(unique = true)
    private String taxId;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "cin_number")
    private String cinNumber;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "website_url")
    private String websiteUrl;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

}
