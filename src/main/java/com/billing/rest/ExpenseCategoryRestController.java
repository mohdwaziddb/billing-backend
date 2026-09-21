package com.billing.rest;

import com.billing.service.ExpenseCategoryService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryRestController {

    private final ExpenseCategoryService expenseCategoryService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseCategoryService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long categoryId) {
        try {
            categoryId = DataTypeUtility.getForeignKeyValue(categoryId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseCategoryService.get(authentication.getName(), categoryId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseCategoryService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long categoryId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseCategoryService.update(param, DataTypeUtility.getForeignKeyValue(categoryId), authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long categoryId) {
        try {
            categoryId = DataTypeUtility.getForeignKeyValue(categoryId);
            expenseCategoryService.delete(authentication.getName(), categoryId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

