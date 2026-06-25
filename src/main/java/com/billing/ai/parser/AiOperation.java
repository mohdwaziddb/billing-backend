package com.billing.ai.parser;

public enum AiOperation {
    UNKNOWN(false),
    CUSTOMER_SEARCH(false),
    PRODUCT_SEARCH(false),
    CURRENT_STOCK(false),
    OUTSTANDING_CUSTOMERS(false),
    INVOICE_SEARCH(false),
    PAYMENT_SEARCH(false),
    SALES_SUMMARY(false),
    COLLECTION_SUMMARY(false),
    EXPENSE_SUMMARY(false),
    INVENTORY_SUMMARY(false),
    PROFIT_SUMMARY(false),
    CREATE_CUSTOMER(true),
    CREATE_PRODUCT(true),
    CREATE_INVOICE(true),
    RECORD_PAYMENT(true);

    private final boolean writeOperation;

    AiOperation(boolean writeOperation) {
        this.writeOperation = writeOperation;
    }

    public boolean isWriteOperation() {
        return writeOperation;
    }
}
