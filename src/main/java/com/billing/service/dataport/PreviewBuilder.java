package com.billing.service.dataport;

import com.billing.dto.dataport.DataPortPreviewResponse;
import com.billing.dto.dataport.ValidatableImportRow;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PreviewBuilder {

    public <T extends ValidatableImportRow> DataPortPreviewResponse<T> build(List<T> rows, Object referenceData) {
        int validRows = (int) rows.stream().filter(ValidatableImportRow::isValid).count();
        return DataPortPreviewResponse.<T>builder()
                .rows(rows)
                .totalRows(rows.size())
                .validRows(validRows)
                .invalidRows(rows.size() - validRows)
                .referenceData(referenceData)
                .build();
    }
}
