package com.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.billing.entity.enums.InventoryConsumptionMethod;
import com.billing.entity.enums.InventoryPricingPolicy;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private StateMaster stateMaster;

    private String pincode;

    @Column(unique = true)
    private String taxId;

    @Column(unique = true)
    private String gstin;

    @Builder.Default
    @Column(nullable = false)
    private boolean gstRegistered = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean compositionScheme = false;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "cin_number")
    private String cinNumber;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_ifsc_code")
    private String bankIfscCode;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "upi_id")
    private String upiId;

    @Column(name = "signature_url")
    private String signatureUrl;

    @Column(name = "invoice_notes", columnDefinition = "TEXT")
    private String invoiceNotes;

    @Column(name = "invoice_terms", columnDefinition = "TEXT")
    private String invoiceTerms;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "is_chatbot_enabled", nullable = false)
    private boolean chatbotEnabled = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_consumption_method", nullable = false)
    private InventoryConsumptionMethod inventoryConsumptionMethod = InventoryConsumptionMethod.FIFO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_pricing_policy", nullable = false)
    private InventoryPricingPolicy inventoryPricingPolicy = InventoryPricingPolicy.LATEST_BATCH_SELLING_PRICE;

}
