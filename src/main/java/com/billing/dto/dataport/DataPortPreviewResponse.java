package com.billing.dto.dataport;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DataPortPreviewResponse<T> {
    private List<T> rows;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private Object referenceData;
}
