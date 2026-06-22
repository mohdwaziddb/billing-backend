package com.billing.dto.dataport;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProductDataPortReferenceData {
    private List<CategoryOption> categories;
    private List<SubCategoryOption> subCategories;
    private List<String> existingSkus;

    @Getter
    @Builder
    public static class CategoryOption {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    public static class SubCategoryOption {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private String name;
    }
}
