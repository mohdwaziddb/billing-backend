package com.billing.dto.dataport;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class ProductDataPortRow implements ValidatableImportRow {
    private Integer rowNumber;
    private String productName;
    private String productCategory;
    private String productSubCategory;
    private String sku;
    private String active;
    private String brand;
    private String hsnCode;
    private String minimumStockQty;
    private String taxPercent;
    private Long productCategoryId;
    private Long productSubCategoryId;
    private Boolean activeValue;
    private boolean valid = true;
    private Map<String, String> validationErrors = new LinkedHashMap<>();
}
