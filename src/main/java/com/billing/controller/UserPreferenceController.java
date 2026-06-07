package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.user.UserPreferenceRequest;
import com.billing.dto.user.UserPreferenceResponse;
import com.billing.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("User preferences fetched successfully",
                userPreferenceService.getPreferences(authentication.getName())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> update(Authentication authentication,
                                                                     @RequestBody UserPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User preferences updated successfully",
                userPreferenceService.updatePreferences(authentication.getName(), request)));
    }
}
