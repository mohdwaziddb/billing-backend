package com.billing.entity.enums;

public enum DocumentSequenceType {
    INVOICE("INV"),
    PURCHASE("PUR");

    private final String prefix;

    DocumentSequenceType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
