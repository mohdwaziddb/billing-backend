package com.billing.rest;

import com.billing.service.UserService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserRestController {

    private final UserService userService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication, HttpServletRequest req) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.getProfile(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/active-referrers")
    @com.billing.security.RequiresPermission(menu = "CREATE_INVOICE", action = "ADD")
    public ResponseEntity<?> activeReferrers(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.activeReferralUsers(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "USERS", action = "VIEW")
    public ResponseEntity<?> listCompanyUsers(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.pageCompanyUsers(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "USERS", action = "ADD")
    public ResponseEntity<?> createCompanyUser(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.createCompanyUser(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{userId}")
    @com.billing.security.RequiresPermission(menu = "USERS", action = "EDIT")
    public ResponseEntity<?> updateCompanyUser(@PathVariable Long userId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            userId = DataTypeUtility.getForeignKeyValue(userId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.updateCompanyUser(param, userId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{userId}")
    @com.billing.security.RequiresPermission(menu = "USERS", action = "DELETE")
    public ResponseEntity<?> deactivateCompanyUser(Authentication authentication, @PathVariable Long userId) {
        try {
            userId = DataTypeUtility.getForeignKeyValue(userId);
            userService.deactivateCompanyUser(authentication.getName(), userId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

