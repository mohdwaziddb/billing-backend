package com.billing.rest;

import com.billing.service.BizioConsoleService;
import com.billing.util.GeneralResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bizio-console")
@RequiredArgsConstructor
@PreAuthorize("principal instanceof T(com.billing.security.TenantSuperAdminPrincipal)")
public class BizioConsoleRestController {

    private final BizioConsoleService bizioConsoleService;

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody(required = false) Map<String, Object> param,
                                   Authentication authentication,
                                   HttpServletRequest req) {
        // NOTE: SanitizeData deliberately NOT applied here — Jsoup cleaning
        // would corrupt SQL operators (<, >, etc.). SQL safety is enforced
        // in BizioConsoleService (SELECT-only + current-DB lock).
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                bizioConsoleService.execute(param, authentication)), HttpStatus.OK);
    }

    @GetMapping("/tables")
    public ResponseEntity<?> tables(Authentication authentication, HttpServletRequest req) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                bizioConsoleService.tables(authentication)), HttpStatus.OK);
    }
}
