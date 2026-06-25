package com.billing.exception;

import org.springframework.security.access.AccessDeniedException;

public class ChatbotDisabledException extends AccessDeniedException {
    public ChatbotDisabledException(String message) {
        super(message);
    }
}
