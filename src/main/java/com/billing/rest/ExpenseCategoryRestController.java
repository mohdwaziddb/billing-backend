package com.billing.rest;

import com.billing.service.ExpenseCategoryService;
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
@RequestMapping("/api/v1/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryRestController {

    private final ExpenseCategoryService expenseCategoryService;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseCategoryService.page(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long categoryId) {
        categoryId = DataTypeUtility.getForeignKeyValue(categoryId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseCategoryService.get(authentication.getName(), categoryId)), HttpStatus.OK);
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseCategoryService.create(param, authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long categoryId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseCategoryService.update(param, DataTypeUtility.getForeignKeyValue(categoryId), authentication.getName())), HttpStatus.OK);
    }

    @DeleteMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "EXPENSE_CATEGORIES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long categoryId) {
        categoryId = DataTypeUtility.getForeignKeyValue(categoryId);
        expenseCategoryService.delete(authentication.getName(), categoryId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
    }
}
