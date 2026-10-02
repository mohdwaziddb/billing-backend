package com.billing.rest;

import com.billing.service.PlatformAdminAuthService;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminAuthRestController {

    private final PlatformAdminAuthService platformAdminAuthService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminAuthService.login(param)), HttpStatus.OK);
    }
}
