package com.billing.rest;

import com.billing.service.ProductCategoryService;
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
@RequestMapping("/api/v1/product-categories")
@RequiredArgsConstructor
public class ProductCategoryRestController {

    private final ProductCategoryService productCategoryService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "PRODUCT_CATEGORY", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productCategoryService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCT_CATEGORY", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long categoryId, HttpServletRequest req) {
        try {
            categoryId = DataTypeUtility.getForeignKeyValue(categoryId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productCategoryService.get(authentication.getName(), categoryId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "PRODUCT_CATEGORY", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productCategoryService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCT_CATEGORY", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long categoryId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productCategoryService.update(param, DataTypeUtility.getForeignKeyValue(categoryId), authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{categoryId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCT_CATEGORY", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long categoryId) {
        try {
            categoryId = DataTypeUtility.getForeignKeyValue(categoryId);
            productCategoryService.delete(authentication.getName(), categoryId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

