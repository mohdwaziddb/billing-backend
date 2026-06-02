package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.user.UserProfileResponse;
import com.billing.saas.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", userService.getProfile(authentication.getName())));
    }
}
