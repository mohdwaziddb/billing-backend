package com.billing.rest;

import com.billing.service.PermissionService;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionRestController {

    private final PermissionService permissionService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", permissionService.effectivePermissions(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/my-menus")
    public ResponseEntity<?> myMenus(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", permissionService.effectivePermissions(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/role-matrix")
    @com.billing.security.RequiresPermission(menu = "ROLE_PERMISSIONS", action = "VIEW")
    public ResponseEntity<?> roleMatrix(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", permissionService.roleMatrix(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/role-matrix")
    @com.billing.security.RequiresPermission(menu = "ROLE_PERMISSIONS", action = "EDIT")
    public ResponseEntity<?> saveRoleMatrix(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", permissionService.saveRoleMatrix(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/user-matrix")
    @com.billing.security.RequiresPermission(menu = "ROLE_PERMISSIONS", action = "VIEW")
    public ResponseEntity<?> userMatrix(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", permissionService.userMatrix(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/user-matrix")
    @com.billing.security.RequiresPermission(menu = "ROLE_PERMISSIONS", action = "EDIT")
    public ResponseEntity<?> saveUserMatrix(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", permissionService.saveUserMatrix(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

