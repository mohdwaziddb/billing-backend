package com.billing.dto.productcategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductCategoryRequest {

    @NotBlank
    @Size(max = 255)
    private String categoryName;

    @Size(max = 1000)
    private String description;

    @NotNull
    private Boolean active;
}
