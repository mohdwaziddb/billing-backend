package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.user.ColumnPreferenceRequest;
import com.billing.dto.user.ColumnPreferenceResponse;
import com.billing.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/column-preferences")
@RequiredArgsConstructor
public class ColumnPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping("/{tableName}")
    public ResponseEntity<ApiResponse<ColumnPreferenceResponse>> get(Authentication authentication,
                                                                     @PathVariable String tableName) {
        return ResponseEntity.ok(ApiResponse.success("Column preference fetched successfully",
                userPreferenceService.getColumnPreference(authentication.getName(), tableName)));
    }

    @PutMapping("/{tableName}")
    public ResponseEntity<ApiResponse<ColumnPreferenceResponse>> update(Authentication authentication,
                                                                        @PathVariable String tableName,
                                                                        @RequestBody ColumnPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Column preference updated successfully",
                userPreferenceService.updateColumnPreference(authentication.getName(), tableName, request)));
    }
}
