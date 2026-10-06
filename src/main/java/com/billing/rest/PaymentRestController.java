package com.billing.rest;

import com.billing.service.PaymentService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
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

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.page(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/{paymentId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long paymentId, HttpServletRequest req) {
        paymentId = DataTypeUtility.getForeignKeyValue(paymentId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.get(authentication.getName(), paymentId)), HttpStatus.OK);
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.create(param, authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/{paymentId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long paymentId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.update(param, DataTypeUtility.getForeignKeyValue(paymentId), authentication.getName())), HttpStatus.OK);
    }

    @DeleteMapping("/{paymentId}")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long paymentId) {
        paymentId = DataTypeUtility.getForeignKeyValue(paymentId);
        paymentService.delete(authentication.getName(), paymentId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
    }

    @PostMapping("/{paymentId}/restore")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "RESTORE")
    public ResponseEntity<?> restore(Authentication authentication, @PathVariable Long paymentId) {
        paymentId = DataTypeUtility.getForeignKeyValue(paymentId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentService.restore(authentication.getName(), paymentId)), HttpStatus.OK);
    }
}
