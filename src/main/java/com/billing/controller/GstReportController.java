package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.gst.GstReportResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.GstReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/gst-reports")
@RequiredArgsConstructor
public class GstReportController {

    private final GstReportService gstReportService;

    @GetMapping("/summary")
    @RequiresPermission(menu = "GST_SUMMARY", action = "VIEW")
    public ResponseEntity<ApiResponse<GstReportResponse>> summary(Authentication authentication,
                                                                  @RequestParam(required = false) LocalDate startDate,
                                                                  @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("GST summary fetched successfully",
                gstReportService.summary(authentication.getName(), startDate, endDate)));
    }
}
