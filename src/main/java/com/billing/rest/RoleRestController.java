package com.billing.rest;

import com.billing.service.RoleService;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleRestController {

    private final RoleService roleService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "ROLE_PERMISSIONS", action = "VIEW")
    public ResponseEntity<?> roles(@RequestParam(required = false) Map<String, Object> param, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", roleService.create(param)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

