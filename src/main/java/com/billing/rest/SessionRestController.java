package com.billing.rest;

import com.billing.security.JwtService;
import com.billing.service.UserSessionService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Devices (login sessions).
 * /me: har logged-in user apne devices (koi menu permission nahi chahiye).
 * /users/{id}: Users page se Owner/Admin (USERS:EDIT).
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionRestController {

    private final UserSessionService userSessionService;
    private final JwtService jwtService;

    @GetMapping("/me")
    public ResponseEntity<?> mySessions(Authentication authentication, HttpServletRequest request) {
        return ok(userSessionService.mySessions(authentication.getName(), currentSessionKey(request)));
    }

    @DeleteMapping("/me/others")
    public ResponseEntity<?> logoutMyOtherDevices(Authentication authentication, HttpServletRequest request) {
        int count = userSessionService.logoutMyOtherDevices(authentication.getName(), currentSessionKey(request));
        return ok(Map.of("loggedOut", count));
    }

    @DeleteMapping("/me/{sessionId}")
    public ResponseEntity<?> logoutMyDevice(@PathVariable Long sessionId, Authentication authentication, HttpServletRequest request) {
        userSessionService.logoutMyDevice(authentication.getName(), DataTypeUtility.getForeignKeyValue(sessionId), currentSessionKey(request));
        return ok(null);
    }

    @GetMapping("/users/{userId}")
    @com.billing.security.RequiresPermission(menu = "USERS", action = "EDIT")
    public ResponseEntity<?> userSessions(@PathVariable Long userId, Authentication authentication, HttpServletRequest request) {
        return ok(userSessionService.userSessions(authentication.getName(), DataTypeUtility.getForeignKeyValue(userId), currentSessionKey(request)));
    }

    @DeleteMapping("/users/{userId}/{sessionId}")
    @com.billing.security.RequiresPermission(menu = "USERS", action = "EDIT")
    public ResponseEntity<?> logoutUserDevice(@PathVariable Long userId, @PathVariable Long sessionId, Authentication authentication) {
        userSessionService.logoutUserDevice(authentication.getName(), DataTypeUtility.getForeignKeyValue(userId), DataTypeUtility.getForeignKeyValue(sessionId));
        return ok(null);
    }

    private ResponseEntity<?> ok(Object data) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", data), HttpStatus.OK);
    }

    /** Request ka apna device ("This device" tag aur khud ko hataane se rokne ke liye). */
    private String currentSessionKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtService.extractSessionKey(header.substring(7));
        } catch (Exception ignored) {
            return null;
        }
    }
}
