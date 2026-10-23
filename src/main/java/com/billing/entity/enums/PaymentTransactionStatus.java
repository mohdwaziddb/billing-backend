package com.billing.entity.enums;

public enum PaymentTransactionStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    EXPIRED,
    CANCELLED;

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == EXPIRED;
    }
}
