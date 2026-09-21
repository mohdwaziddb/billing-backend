package com.billing.rest;

import com.billing.service.ExpenseService;
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
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseRestController {

    private final ExpenseService expenseService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "EXPENSES", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{expenseId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSES", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long expenseId, HttpServletRequest req) {
        try {
            expenseId = DataTypeUtility.getForeignKeyValue(expenseId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseService.get(authentication.getName(), expenseId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "EXPENSES", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{expenseId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSES", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long expenseId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseService.update(param, DataTypeUtility.getForeignKeyValue(expenseId), authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{expenseId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long expenseId) {
        try {
            expenseId = DataTypeUtility.getForeignKeyValue(expenseId);
            expenseService.delete(authentication.getName(), expenseId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/profitability/customer/{customerId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSES", action = "VIEW")
    public ResponseEntity<?> customerProfitability(Authentication authentication, @PathVariable Long customerId, @RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseService.customerProfitability(param, authentication.getName(), DataTypeUtility.getForeignKeyValue(customerId))), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/profitability/invoice/{invoiceId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSES", action = "VIEW")
    public ResponseEntity<?> invoiceProfitability(Authentication authentication, @PathVariable Long invoiceId) {
        try {
            invoiceId = DataTypeUtility.getForeignKeyValue(invoiceId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseService.invoiceProfitability(authentication.getName(), invoiceId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/reports/profit-loss")
    @com.billing.security.RequiresPermission(menu = "PROFIT_LOSS", action = "VIEW")
    public ResponseEntity<?> profitLossReport(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseService.profitLossReport(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

