package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.platform.PlatformSettingsResponse;
import com.billing.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-settings")
@RequiredArgsConstructor
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> settings() {
        return ResponseEntity.ok(ApiResponse.success("Platform settings fetched successfully",
                platformSettingsService.getSettings()));
    }
}
