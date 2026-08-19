package com.billing.exception;

public class ChatbotDisabledException extends RuntimeException {
    public ChatbotDisabledException(String message) {
        super(message);
    }
}