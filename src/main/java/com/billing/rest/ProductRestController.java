package com.billing.rest;

import com.billing.service.ProductService;
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
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductRestController {

    private final ProductService productService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "PRODUCTS", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{productId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCTS", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long productId, HttpServletRequest req) {
        try {
            productId = DataTypeUtility.getForeignKeyValue(productId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productService.get(authentication.getName(), productId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "PRODUCTS", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{productId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCTS", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long productId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productService.update(param, DataTypeUtility.getForeignKeyValue(productId), authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{productId}")
    @com.billing.security.RequiresPermission(menu = "PRODUCTS", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long productId) {
        try {
            productId = DataTypeUtility.getForeignKeyValue(productId);
            productService.delete(authentication.getName(), productId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/bulk-delete")
    @com.billing.security.RequiresPermission(menu = "PRODUCTS", action = "DELETE")
    public ResponseEntity<?> bulkDelete(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", productService.deleteBulk(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

