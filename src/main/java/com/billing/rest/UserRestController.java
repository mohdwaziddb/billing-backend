package com.billing.rest;

import com.billing.service.UserService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserRestController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication, HttpServletRequest req) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.getProfile(authentication.getName())), HttpStatus.OK);
    }

    // Apni profile: har logged-in user (koi menu permission nahi chahiye), sirf khud ka record.
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, Object> param, Authentication authentication) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.updateOwnProfile(param, authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/me/photo")
    public ResponseEntity<?> uploadMyPhoto(@RequestParam("photo") MultipartFile photo, Authentication authentication) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.uploadOwnPhoto(authentication.getName(), photo)), HttpStatus.OK);
    }

    @DeleteMapping("/me/photo")
    public ResponseEntity<?> deleteMyPhoto(Authentication authentication) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.deleteOwnPhoto(authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/{userId}/photo")
    @com.billing.security.RequiresPermission(menu = "USERS", action = "EDIT")
    public ResponseEntity<?> uploadUserPhoto(@PathVariable Long userId, @RequestParam("photo") MultipartFile photo, Authentication authentication) {
        userId = DataTypeUtility.getForeignKeyValue(userId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.uploadUserPhoto(authentication.getName(), userId, photo)), HttpStatus.OK);
    }

    @DeleteMapping("/{userId}/photo")
    @com.billing.security.RequiresPermission(menu = "USERS", action = "EDIT")
    public ResponseEntity<?> deleteUserPhoto(@PathVariable Long userId, Authentication authentication) {
        userId = DataTypeUtility.getForeignKeyValue(userId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.deleteUserPhoto(authentication.getName(), userId)), HttpStatus.OK);
    }

    @GetMapping("/active-referrers")
    @com.billing.security.RequiresPermission(menu = "CREATE_INVOICE", action = "ADD")
    public ResponseEntity<?> activeReferrers(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.activeReferralUsers(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "USERS", action = "VIEW")
    public ResponseEntity<?> listCompanyUsers(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.pageCompanyUsers(param, authentication.getName())), HttpStatus.OK);
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "USERS", action = "ADD")
    public ResponseEntity<?> createCompanyUser(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        // Password raw rahe: login raw password se match karta hai.
        param = SanitizeData.sanitizeMapObjPreservingSecrets(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.createCompanyUser(param, authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/{userId}")
    @com.billing.security.RequiresPermission(menu = "USERS", action = "EDIT")
    public ResponseEntity<?> updateCompanyUser(@PathVariable Long userId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObjPreservingSecrets(param);
        userId = DataTypeUtility.getForeignKeyValue(userId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userService.updateCompanyUser(param, userId, authentication.getName())), HttpStatus.OK);
    }

    @DeleteMapping("/{userId}")
    @com.billing.security.RequiresPermission(menu = "USERS", action = "DELETE")
    public ResponseEntity<?> deactivateCompanyUser(Authentication authentication, @PathVariable Long userId) {
        userId = DataTypeUtility.getForeignKeyValue(userId);
        userService.deactivateCompanyUser(authentication.getName(), userId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
    }
}
