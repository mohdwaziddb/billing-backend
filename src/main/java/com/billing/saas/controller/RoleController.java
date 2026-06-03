package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.entity.enums.RoleName;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> roles() {
        return ResponseEntity.ok(ApiResponse.success("Roles fetched successfully",
                Arrays.stream(RoleName.values()).map(Enum::name).toList()));
    }
}
