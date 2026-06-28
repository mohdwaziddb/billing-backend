package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.state.StateResponse;
import com.billing.service.StateMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/states")
@RequiredArgsConstructor
public class StateMasterController {

    private final StateMasterService stateMasterService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StateResponse>>> list(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("States fetched successfully", stateMasterService.listActive()));
    }
}
