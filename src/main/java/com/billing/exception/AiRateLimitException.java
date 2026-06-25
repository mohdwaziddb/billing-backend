package com.billing.exception;

public class AiRateLimitException extends RuntimeException {
    public AiRateLimitException(String message) {
        super(message);
    }
}
