package com.billing.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyThemeRequest {
    // Accepts a solid "#RRGGBB" or a gradient "#RRGGBB,#RRGGBB" (primary,accent).
    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}(,#[0-9A-Fa-f]{6})?$", message = "Theme color must be #HEX or a gradient #HEX,#HEX")
    private String themeColor;
}
