package com.billing.rest;

import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.security.RequiresPermission;
import com.billing.service.MfaService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/mfa")
@RequiredArgsConstructor
public class MfaRestController {

    private static final Set<String> SENSITIVE_KEYS = Set.of("code", "otp");

    private final MfaService mfaService;

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam(required = false) Map<String, Object> param,
                                    Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                mfaService.getStatus(param, authentication.getName())), HttpStatus.OK);
    }

    @PostMapping("/setup/start")
    public ResponseEntity<?> startSetup(@RequestBody Map<String, Object> param,
                                        Authentication authentication, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "QR ready. Scan it with your authenticator app.",
                    mfaService.startSetup(param, authentication.getName())), HttpStatus.OK);
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/setup/confirm")
    public ResponseEntity<?> confirmSetup(@RequestBody Map<String, Object> param,
                                          Authentication authentication, HttpServletRequest request) {
        try {
            param = sanitizePreservingSecrets(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "MFA enabled successfully",
                    mfaService.confirmSetup(param, authentication.getName())), HttpStatus.OK);
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new GeneralResponse<>(false, e.getMessage(), null));
        }
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<?> deleteDevice(@PathVariable Long deviceId,
                                          @RequestBody(required = false) Map<String, Object> param,
                                          Authentication authentication, HttpServletRequest req) {
        try {
            deviceId = DataTypeUtility.getForeignKeyValue(deviceId);
            String code = param == null ? null : DataTypeUtility.stringValue(param.get("code"));
            mfaService.deleteDevice(authentication.getName(), deviceId, code);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Device removed", Map.of("status", "ok")), HttpStatus.OK);
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/admin/reset")
    @RequiresPermission(menu = "USERS", action = "EDIT")
    public ResponseEntity<?> resetUserMfa(@RequestBody Map<String, Object> param,
                                          Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            Long userId = DataTypeUtility.getForeignKeyValue(param.get("userId"));
            if (userId == null) {
                userId = DataTypeUtility.getForeignKeyValue(param.get("user_id"));
            }
            return new ResponseEntity<>(new GeneralResponse<>(true, "MFA has been reset. The user needs to enroll again.",
                    mfaService.resetUserMfa(authentication.getName(), userId)), HttpStatus.OK);
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new GeneralResponse<>(false, e.getMessage(), null));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new GeneralResponse<>(false, e.getMessage(), null));
        }
    }

    private Map<String, Object> sanitizePreservingSecrets(Map<String, Object> param) {
        if (param == null) {
            return new java.util.HashMap<>();
        }
        Map<String, Object> secrets = new java.util.HashMap<>();
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
