package com.billing.exception;

public class CompanyInactiveException extends RuntimeException {
    public CompanyInactiveException(String message) {
        super(message);
    }
}
