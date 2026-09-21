package com.billing.rest;

import com.billing.service.PaymentService;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentRestController {

    private final PaymentService paymentService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{paymentId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long paymentId, HttpServletRequest req) {
        try {
            paymentId = DataTypeUtility.getForeignKeyValue(paymentId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.get(authentication.getName(), paymentId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{paymentId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long paymentId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.update(param, DataTypeUtility.getForeignKeyValue(paymentId), authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{paymentId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long paymentId) {
        try {
            paymentId = DataTypeUtility.getForeignKeyValue(paymentId);
            paymentService.delete(authentication.getName(), paymentId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/{paymentId}/restore")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "RESTORE")
    public ResponseEntity<?> restore(Authentication authentication, @PathVariable Long paymentId) {
        try {
            paymentId = DataTypeUtility.getForeignKeyValue(paymentId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.restore(authentication.getName(), paymentId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

