package com.billing.rest;

import com.billing.exception.BadRequestException;
import com.billing.exception.CompanyInactiveException;
import com.billing.exception.UnauthorizedException;
import com.billing.service.AuthService;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "newPassword", "refreshToken");

    private final AuthService authService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Login successful", authService.login(param)), HttpStatus.OK);
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (CompanyInactiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            authService.forgotPassword(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Password updated successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (CompanyInactiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Token refreshed successfully", authService.refresh(param)), HttpStatus.OK);
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (CompanyInactiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            authService.logout(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Logged out successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    private Map<String, Object> sanitizePreservingSecrets(Map<String, Object> param) {
        if (param == null) {
            return new HashMap<>();
        }
        Map<String, Object> secrets = new HashMap<>();
        for (String key : SENSITIVE_KEYS) {
            if (param.containsKey(key) && param.get(key) != null) {
                secrets.put(key, param.get(key).toString());
            }
        }
        Map<String, Object> sanitized = SanitizeData.sanitizeMapObj(param);
        sanitized.putAll(secrets);
        return sanitized;
    }
}
