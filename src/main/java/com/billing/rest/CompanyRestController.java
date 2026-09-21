package com.billing.rest;

import com.billing.security.RequiresPermission;
import com.billing.service.CompanyService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
public class CompanyRestController {

    private final CompanyService companyService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @RequiresPermission(menu = "ABOUT_COMPANY", action = "VIEW")
    public ResponseEntity<?> settings(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.getSettings(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping
    @RequiresPermission(menu = "ABOUT_COMPANY", action = "EDIT")
    public ResponseEntity<?> update(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.updateSettings(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/logo")
    @RequiresPermission(menu = "ABOUT_COMPANY", action = "EDIT")
    public ResponseEntity<?> uploadLogo(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, @RequestParam("logo") MultipartFile logo, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.uploadLogo(authentication.getName(), logo)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/signature")
    @RequiresPermission(menu = "ABOUT_COMPANY", action = "EDIT")
    public ResponseEntity<?> uploadSignature(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, @RequestParam("signature") MultipartFile signature, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.uploadSignature(authentication.getName(), signature)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/logo")
    @RequiresPermission(menu = "ABOUT_COMPANY", action = "EDIT")
    public ResponseEntity<?> deleteLogo(Authentication authentication, HttpServletRequest req) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.deleteLogo(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/signature")
    @RequiresPermission(menu = "ABOUT_COMPANY", action = "EDIT")
    public ResponseEntity<?> deleteSignature(Authentication authentication, HttpServletRequest req) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.deleteSignature(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/theme")
    public ResponseEntity<?> theme(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.theme(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/theme")
    @RequiresPermission(menu = "THEME_SETTINGS", action = "EDIT")
    public ResponseEntity<?> updateTheme(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.updateTheme(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/theme/reset")
    @RequiresPermission(menu = "THEME_SETTINGS", action = "EDIT")
    public ResponseEntity<?> resetTheme(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyService.resetTheme(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

