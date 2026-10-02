package com.billing.rest;

import com.billing.service.PurchaseService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseRestController {

    private final PurchaseService purchaseService;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "PURCHASES", action = "VIEW")
    public ResponseEntity<?> page(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", purchaseService.page(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/{purchaseId}")
    @com.billing.security.RequiresPermission(menu = "PURCHASES", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long purchaseId, HttpServletRequest req) {
        purchaseId = DataTypeUtility.getForeignKeyValue(purchaseId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", purchaseService.get(authentication.getName(), purchaseId)), HttpStatus.OK);
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "PURCHASES", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", purchaseService.create(param, authentication.getName())), HttpStatus.OK);
    }

    @DeleteMapping("/{purchaseId}")
    @com.billing.security.RequiresPermission(menu = "PURCHASES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long purchaseId) {
        purchaseId = DataTypeUtility.getForeignKeyValue(purchaseId);
        purchaseService.delete(authentication.getName(), purchaseId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
    }
}
