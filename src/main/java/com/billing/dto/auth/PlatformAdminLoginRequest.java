package com.billing.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformAdminLoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
