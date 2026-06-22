package com.billing.dto.productsubcategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductSubCategoryRequest {

    @NotNull
    private Long categoryId;

    @NotBlank
    @Size(max = 255)
    private String subCategoryName;

    @Size(max = 1000)
    private String description;

    @NotNull
    private Boolean active;
}
