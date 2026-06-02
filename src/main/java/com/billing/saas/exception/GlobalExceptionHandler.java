package com.billing.saas.exception;

import com.billing.saas.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message("Validation failed")
                .data(errors)
                .timestamp(java.time.LocalDateTime.now())
                .build());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication failed"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Request data is invalid. Please check the entered values."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Invalid request data"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(resolveDataIntegrityMessage(ex)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("You do not have permission to access this resource"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }

    private String resolveDataIntegrityMessage(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (message == null || message.isBlank()) {
            return "This record could not be saved because it conflicts with existing data.";
        }

        String normalized = message.toLowerCase();
        if (normalized.contains("doesn't have a default value") && message.contains("'")) {
            String fieldName = extractQuotedValue(message);
            if (fieldName != null) {
                return "Database schema mismatch: field '" + fieldName + "' is still required in MySQL. Please update the table structure or restart after migration cleanup.";
            }
        }

        if (normalized.contains("duplicate entry")) {
            return "This record already exists with the same unique value.";
        }

        if (normalized.contains("cannot be null") && message.contains("'")) {
            String fieldName = extractQuotedValue(message);
            if (fieldName != null) {
                return "Required database field missing: " + fieldName;
            }
        }

        return "This record could not be saved because it conflicts with existing data.";
    }

    private String extractQuotedValue(String message) {
        int firstQuote = message.indexOf('\'');
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = message.indexOf('\'', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return message.substring(firstQuote + 1, secondQuote);
    }
}
