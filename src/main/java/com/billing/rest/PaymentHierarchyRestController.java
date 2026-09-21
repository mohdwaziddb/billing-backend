package com.billing.rest;

import com.billing.service.PaymentHierarchyService;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment-hierarchy")
@RequiredArgsConstructor
public class PaymentHierarchyRestController {

    private final PaymentHierarchyService paymentHierarchyService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping("/children")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_HIERARCHY", action = "VIEW")
    public ResponseEntity<?> children(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentHierarchyService.children(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/summary")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_HIERARCHY", action = "VIEW")
    public ResponseEntity<?> summary(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentHierarchyService.summary(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

