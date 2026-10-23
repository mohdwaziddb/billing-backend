package com.billing.entity;

import com.billing.entity.enums.PaymentGatewayType;
import com.billing.entity.enums.PaymentTransactionChannel;
import com.billing.entity.enums.PaymentTransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ek online payment attempt (Checkout order ya Payment Link). SUCCESS hone
 * par hi Payment row banti hai; payment field us row ko point karta hai.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_txn_ref", columnNames = {"txn_ref"})
}, indexes = {
        @Index(name = "idx_payment_txn_order", columnList = "gateway_order_id"),
        @Index(name = "idx_payment_txn_link", columnList = "gateway_link_id"),
        @Index(name = "idx_payment_txn_invoice", columnList = "invoice_id"),
        @Index(name = "idx_payment_txn_status", columnList = "status")
})
public class PaymentTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    private Company company;

    @Column(name = "txn_ref", nullable = false, length = 40)
    private String txnRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_type", nullable = false, length = 40)
    private PaymentGatewayType gatewayType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTransactionChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTransactionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(name = "gateway_link_id", length = 100)
    private String gatewayLinkId;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    // upi / card / netbanking / wallet — jo gateway bataye.
    @Column(length = 40)
    private String method;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "last_gateway_response", columnDefinition = "LONGTEXT")
    private String lastGatewayResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "initiated_by")
    private String initiatedBy;
}
