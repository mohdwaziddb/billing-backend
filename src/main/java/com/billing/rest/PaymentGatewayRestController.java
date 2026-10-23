package com.billing.rest;

import com.billing.exception.BadRequestException;
import com.billing.service.payment.OnlinePaymentService;
import com.billing.service.payment.PaymentGatewaySettingService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment-gateway")
@RequiredArgsConstructor
public class PaymentGatewayRestController {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayRestController.class);

    private final PaymentGatewaySettingService paymentGatewaySettingService;
    private final OnlinePaymentService onlinePaymentService;

    @GetMapping("/providers")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_GATEWAY", action = "VIEW")
    public ResponseEntity<?> providers() {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentGatewaySettingService.providers()), HttpStatus.OK);
    }

    @GetMapping("/settings")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_GATEWAY", action = "VIEW")
    public ResponseEntity<?> settings(Authentication authentication) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentGatewaySettingService.list(authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/settings")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_GATEWAY", action = "EDIT")
    public ResponseEntity<?> saveSettings(@RequestBody Map<String, Object> param, Authentication authentication) {
        param = sanitizeKeepingGatewayConfig(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentGatewaySettingService.save(param, authentication.getName())), HttpStatus.OK);
    }

    @PostMapping("/settings/test")
    @com.billing.security.RequiresPermission(menu = "PAYMENT_GATEWAY", action = "EDIT")
    public ResponseEntity<?> testSettings(@RequestBody Map<String, Object> param, Authentication authentication) {
        param = sanitizeKeepingGatewayConfig(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentGatewaySettingService.test(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/config")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<?> config() {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", paymentGatewaySettingService.publicConfig()), HttpStatus.OK);
    }

    @PostMapping("/transactions/initiate")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "ADD")
    public ResponseEntity<?> initiate(@RequestBody Map<String, Object> param, Authentication authentication) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", onlinePaymentService.initiate(param, authentication.getName())), HttpStatus.OK);
    }

    @PostMapping("/transactions/{txnRef}/verify")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "ADD")
    public ResponseEntity<?> verify(@PathVariable String txnRef, @RequestBody Map<String, Object> param, Authentication authentication) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", onlinePaymentService.verify(txnRef, param, authentication.getName())), HttpStatus.OK);
    }

    @PostMapping("/transactions/{txnRef}/cancel")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "ADD")
    public ResponseEntity<?> cancel(@PathVariable String txnRef, Authentication authentication) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", onlinePaymentService.cancel(txnRef, authentication.getName())), HttpStatus.OK);
    }

    // POST (write pool): status refresh me settle karke Payment bhi ban sakta hai.
    @PostMapping("/transactions/{txnRef}/refresh")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<?> refresh(@PathVariable String txnRef, Authentication authentication) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", onlinePaymentService.refresh(txnRef, authentication.getName())), HttpStatus.OK);
    }

    @PostMapping("/transactions/{txnRef}/send-link")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "ADD")
    public ResponseEntity<?> sendLink(@PathVariable String txnRef, @RequestBody Map<String, Object> param, Authentication authentication) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", onlinePaymentService.sendLink(txnRef, param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/transactions")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<?> transactions(@RequestParam(required = false) Map<String, Object> param, Authentication authentication) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", onlinePaymentService.list(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/transactions/{txnRef}")
    @com.billing.security.RequiresPermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<?> transaction(@PathVariable String txnRef, Authentication authentication) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", onlinePaymentService.get(txnRef, authentication.getName())), HttpStatus.OK);
    }

    /**
     * Gateway server-to-server callback (permitAll; no JWT). Tenant
     * ?company_code= se RequestInterceptor resolve karta hai; authenticity
     * gateway signature (raw body) se. Raw body sanitize nahi hoti — signature toot jayega.
     */
    @PostMapping("/webhook/{gatewayCode}")
    public ResponseEntity<?> webhook(@PathVariable String gatewayCode, @RequestBody(required = false) String rawBody, HttpServletRequest request) {
        String companyCode = request.getParameter("company_code");
        if (com.billing.core.Registry.IS_ONLINE && (companyCode == null || companyCode.isBlank())) {
            // Bina company_code default DB par kabhi mat jao.
            return new ResponseEntity<>(Map.of("status", "rejected"), HttpStatus.BAD_REQUEST);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, request.getHeader(name));
        }
        try {
            String outcome = onlinePaymentService.handleWebhook(gatewayCode, rawBody == null ? "" : rawBody, headers);
            log.info("[PaymentWebhook] {} -> {}", gatewayCode, outcome);
            return new ResponseEntity<>(Map.of("status", "ok"), HttpStatus.OK);
        } catch (BadRequestException exception) {
            log.warn("[PaymentWebhook] {} rejected: {}", gatewayCode, exception.getMessage());
            return new ResponseEntity<>(Map.of("status", "rejected"), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Baaki fields sanitize, par gateway "config" (API keys / secrets) as-is:
     * HTML cleaner "<", ">" jaise characters badal deta tha aur secret gateway
     * se match nahi karta. Ye values encrypted store hoti hain, kabhi HTML me
     * render nahi hoti, aur service sirf plain strings hi leti hai.
     */
    private Map<String, Object> sanitizeKeepingGatewayConfig(Map<String, Object> param) {
        Object rawConfig = param == null ? null : param.get("config");
        Map<String, Object> sanitized = SanitizeData.sanitizeMapObj(param);
        if (rawConfig instanceof Map<?, ?> rawMap) {
            Map<String, Object> config = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                Object value = entry.getValue();
                if (entry.getKey() != null && (value == null || value instanceof String)) {
                    config.put(DataTypeUtility.stringValue(entry.getKey()), value);
                }
            }
            sanitized.put("config", config);
        }
        return sanitized;
    }
}
