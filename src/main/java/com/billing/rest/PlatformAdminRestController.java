package com.billing.rest;

import com.billing.service.PlatformAdminService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform-admin")
@RequiredArgsConstructor
@PreAuthorize("principal instanceof T(com.billing.security.PlatformAdminPrincipal)")
public class PlatformAdminRestController {

    private final PlatformAdminService platformAdminService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.dashboard()), HttpStatus.OK);
    }

    @GetMapping("/companies")
    public ResponseEntity<?> companies(@RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.companies(param)), HttpStatus.OK);
    }

    @GetMapping("/companies/overview")
    public ResponseEntity<?> companyOverview(@RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.companyOverview(param)), HttpStatus.OK);
    }

    @PostMapping("/companies/{companyCode}/activate")
    public ResponseEntity<?> activateCompany(@PathVariable String companyCode) {
        companyCode = companyCode == null ? null : companyCode.trim();
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.activateCompany(companyCode)), HttpStatus.OK);
    }

    @PostMapping("/companies/{companyCode}/deactivate")
    public ResponseEntity<?> deactivateCompany(@PathVariable String companyCode) {
        companyCode = companyCode == null ? null : companyCode.trim();
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.deactivateCompany(companyCode)), HttpStatus.OK);
    }

    @PostMapping("/companies/{companyCode}/repair-seeds")
    public ResponseEntity<?> repairCompanySeeds(@PathVariable String companyCode) {
        companyCode = companyCode == null ? null : companyCode.trim();
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.repairCompanySeeds(companyCode)), HttpStatus.OK);
    }

    @PostMapping("/companies/{companyCode}/chatbot/enable")
    public ResponseEntity<?> enableCompanyChatbot(@PathVariable String companyCode) {
        companyCode = companyCode == null ? null : companyCode.trim();
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.setCompanyChatbotEnabled(companyCode, true)), HttpStatus.OK);
    }

    @PostMapping("/companies/{companyCode}/chatbot/disable")
    public ResponseEntity<?> disableCompanyChatbot(@PathVariable String companyCode) {
        companyCode = companyCode == null ? null : companyCode.trim();
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.setCompanyChatbotEnabled(companyCode, false)), HttpStatus.OK);
    }

    @PostMapping("/companies/{companyCode}/super-admin/reset")
    public ResponseEntity<?> resetSuperAdminPassword(@PathVariable String companyCode, @RequestBody(required = false) Map<String, Object> param) {
        companyCode = companyCode == null ? null : companyCode.trim();
        if (param == null) {
            param = new HashMap<>();
        }
        param = SanitizeData.sanitizeMapObj(param);
        String password = DataTypeUtility.stringValue(param.get("password"));
        if (password.length() == 0) {
            password = DataTypeUtility.stringValue(param.get("newPassword"));
        }
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.resetSuperAdminPassword(companyCode, password)), HttpStatus.OK);
    }

    @GetMapping("/companies/{companyCode}")
    public ResponseEntity<?> companyDetails(@PathVariable String companyCode) {
        companyCode = companyCode == null ? null : companyCode.trim();
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.companyDetails(companyCode)), HttpStatus.OK);
    }

    @GetMapping("/settings")
    public ResponseEntity<?> settings() {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.settings()), HttpStatus.OK);
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.updateSettings(param)), HttpStatus.OK);
    }
}
