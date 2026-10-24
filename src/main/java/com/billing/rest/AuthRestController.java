package com.billing.rest;

import com.billing.exception.BadRequestException;
import com.billing.exception.CompanyInactiveException;
import com.billing.exception.UnauthorizedException;
import com.billing.security.CustomUserDetails;
import com.billing.service.AuthService;
import com.billing.service.PasswordResetService;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

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
        }
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyMfa(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Login successful", authService.verifyMfa(param)), HttpStatus.OK);
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (CompanyInactiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/forgot-password/request")
    public ResponseEntity<?> forgotPasswordRequest(@RequestBody Map<String, Object> param,
                                                   Authentication authentication,
                                                   HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            // Logged-in dashboard flow: a valid Bearer token means the OTP
            // recipient must be the authenticated user — the posted
            // identifier is ignored (tamper-proof against DevTools edits).
            Long authenticatedUserId = resolveAuthenticatedUserId(authentication);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                    passwordResetService.requestOtp(param, authenticatedUserId)), HttpStatus.OK);
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (CompanyInactiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/forgot-password/verify")
    public ResponseEntity<?> forgotPasswordVerify(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            passwordResetService.verifyOtp(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "OTP verified successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (CompanyInactiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/forgot-password/confirm")
    public ResponseEntity<?> forgotPasswordConfirm(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            passwordResetService.confirmOtp(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Password updated successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (CompanyInactiveException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
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
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        param = sanitizePreservingSecrets(param);
        authService.logout(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Logged out successfully", Map.of("status", "ok")), HttpStatus.OK);
    }

    /**
     * Returns the id of the authenticated dashboard user, or null for
     * anonymous / platform-admin / super-admin principals. Only a real
     * CustomUserDetails principal (valid user Bearer token, set by
     * JwtAuthenticationFilter even on permitAll routes) counts here.
     */
    private Long resolveAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details) {
            return details.getId();
        }
        return null;
    }

    // confirmPassword bhi raw rahe (pehle sanitize hota tha to "must match" galat fail).
    private Map<String, Object> sanitizePreservingSecrets(Map<String, Object> param) {
        return SanitizeData.sanitizeMapObjPreservingSecrets(param);
    }
}
