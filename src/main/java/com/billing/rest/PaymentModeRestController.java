package com.billing.rest;

import com.billing.service.PaymentModeMasterService;
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
@RequestMapping("/api/v1/payment-modes")
@RequiredArgsConstructor
public class PaymentModeRestController {

    private final PaymentModeMasterService paymentModeMasterService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "PAYMENT_MODES", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentModeMasterService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{modeId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_MODES", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long modeId, HttpServletRequest req) {
        try {
            modeId = DataTypeUtility.getForeignKeyValue(modeId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentModeMasterService.get(authentication.getName(), modeId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "PAYMENT_MODES", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentModeMasterService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{modeId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_MODES", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long modeId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentModeMasterService.update(param, DataTypeUtility.getForeignKeyValue(modeId), authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{modeId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_MODES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long modeId) {
        try {
            modeId = DataTypeUtility.getForeignKeyValue(modeId);
            paymentModeMasterService.delete(authentication.getName(), modeId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

