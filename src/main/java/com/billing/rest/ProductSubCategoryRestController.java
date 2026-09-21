package com.billing.rest;

import com.billing.service.ProductSubCategoryService;
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
@RequestMapping("/api/v1/product-sub-categories")
@RequiredArgsConstructor
public class ProductSubCategoryRestController {

    private final ProductSubCategoryService productSubCategoryService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "PRODUCT_SUB_CATEGORIES", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productSubCategoryService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{subCategoryId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCT_SUB_CATEGORIES", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long subCategoryId, HttpServletRequest req) {
        try {
            subCategoryId = DataTypeUtility.getForeignKeyValue(subCategoryId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productSubCategoryService.get(authentication.getName(), subCategoryId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "PRODUCT_SUB_CATEGORIES", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productSubCategoryService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{subCategoryId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCT_SUB_CATEGORIES", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long subCategoryId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productSubCategoryService.update(param, DataTypeUtility.getForeignKeyValue(subCategoryId), authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{subCategoryId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCT_SUB_CATEGORIES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long subCategoryId) {
        try {
            subCategoryId = DataTypeUtility.getForeignKeyValue(subCategoryId);
            productSubCategoryService.delete(authentication.getName(), subCategoryId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

