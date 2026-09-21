package com.billing.rest;

import com.billing.service.TaxMasterService;
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
@RequestMapping("/api/v1/tax-master")
@RequiredArgsConstructor
public class TaxMasterRestController {

    private final TaxMasterService taxMasterService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "TAX_MASTER", action = "VIEW")
    public ResponseEntity<?> page(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", taxMasterService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/list")
    @com.billing.security.RequiresPermission(menu = "TAX_MASTER", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", taxMasterService.list(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{taxId}")
    @com.billing.security.RequiresPermission(menu = "TAX_MASTER", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long taxId, HttpServletRequest req) {
        try {
            taxId = DataTypeUtility.getForeignKeyValue(taxId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", taxMasterService.get(authentication.getName(), taxId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "TAX_MASTER", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", taxMasterService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{taxId}")
    @com.billing.security.RequiresPermission(menu = "TAX_MASTER", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long taxId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            taxId = DataTypeUtility.getForeignKeyValue(taxId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", taxMasterService.update(param, taxId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{taxId}")
    @com.billing.security.RequiresPermission(menu = "TAX_MASTER", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long taxId) {
        try {
            taxId = DataTypeUtility.getForeignKeyValue(taxId);
            taxMasterService.delete(authentication.getName(), taxId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

