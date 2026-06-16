package com.billing.dto.dataport;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProductDataPortImportRequest {
    private List<ProductDataPortRow> rows;
}
