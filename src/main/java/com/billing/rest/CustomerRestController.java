package com.billing.rest;

import com.billing.security.RequiresPermission;
import com.billing.service.CustomerService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerRestController {

    private final CustomerService customerService;

    @GetMapping
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", customerService.page(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/{customerId}")
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<?> get(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, @PathVariable Long customerId, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        Long customerIdValue = DataTypeUtility.getForeignKeyValue(customerId);
        if (customerIdValue == null) {
            customerIdValue = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        }
        if (customerIdValue == null) {
            customerIdValue = customerId;
        }
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", customerService.get(authentication.getName(), DataTypeUtility.getForeignKeyValue(customerIdValue))), HttpStatus.OK);
    }

    @GetMapping("/by-mobile")
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<?> getByMobile(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", customerService.getByMobile(param, authentication.getName())), HttpStatus.OK);
    }

    @PostMapping
    @RequiresPermission(menu = "CUSTOMERS", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", customerService.create(param, authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/{customerId}")
    @RequiresPermission(menu = "CUSTOMERS", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long customerId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        Long customerIdValue = DataTypeUtility.getForeignKeyValue(customerId);
        if (customerIdValue == null) {
            customerIdValue = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        }
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", customerService.update(param, DataTypeUtility.getForeignKeyValue(customerIdValue), authentication.getName())), HttpStatus.OK);
    }

    @DeleteMapping("/{customerId}")
    @RequiresPermission(menu = "CUSTOMERS", action = "DELETE")
    public ResponseEntity<?> delete(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, @PathVariable Long customerId, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        Long customerIdValue = DataTypeUtility.getForeignKeyValue(customerId);
        if (customerIdValue == null) {
            customerIdValue = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        }
        if (customerIdValue == null) {
            customerIdValue = customerId;
        }
        customerService.delete(authentication.getName(), DataTypeUtility.getForeignKeyValue(customerIdValue));
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
    }

    @GetMapping("/{customerId}/ledger")
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<?> ledger(@PathVariable Long customerId, @RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        Long customerIdValue = DataTypeUtility.getForeignKeyValue(customerId);
        if (customerIdValue == null) {
            customerIdValue = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        }
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", customerService.ledger(param, authentication.getName(), DataTypeUtility.getForeignKeyValue(customerIdValue))), HttpStatus.OK);
    }

    @GetMapping("/{customerId}/purchase-history")
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<?> purchaseHistory(@PathVariable Long customerId, @RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        Long customerIdValue = DataTypeUtility.getForeignKeyValue(customerId);
        if (customerIdValue == null) {
            customerIdValue = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        }
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", customerService.purchaseHistory(param, authentication.getName(), DataTypeUtility.getForeignKeyValue(customerIdValue))), HttpStatus.OK);
    }

    @GetMapping("/outstanding")
    @RequiresPermission(menu = "OUTSTANDING", action = "VIEW")
    public ResponseEntity<?> outstanding(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", customerService.outstanding(param, authentication.getName())), HttpStatus.OK);
    }
}
