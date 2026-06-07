package com.billing.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyThemeRequest {
    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Theme color must be a valid HEX color")
    private String themeColor;
}
