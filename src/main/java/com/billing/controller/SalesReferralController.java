package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.referral.SalesReferralReportResponse;
import com.billing.security.RequirePermission;
import com.billing.service.SalesReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/sales-referrals")
@RequiredArgsConstructor
public class SalesReferralController {

    private final SalesReferralService salesReferralService;

    @GetMapping("/report")
    @RequirePermission(menu = "SALES_REFERRALS", action = "VIEW")
    public ResponseEntity<ApiResponse<SalesReferralReportResponse>> report(Authentication authentication,
                                                                           @RequestParam(required = false) LocalDate startDate,
                                                                           @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Sales referrals report fetched successfully",
                salesReferralService.report(authentication.getName(), startDate, endDate)));
    }
}
