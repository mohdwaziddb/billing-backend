package com.billing.service.dataport;

import com.billing.dto.dataport.ValidatableImportRow;
import com.billing.entity.Company;

import java.util.List;
import java.util.Map;

public interface DataPortDefinition<T extends ValidatableImportRow, C> {

    String getModuleKey();

    String getSampleFileName();

    String getSheetName();

    List<String> getColumns();

    List<List<String>> getSampleRows();

    T mapRow(int rowNumber, Map<String, String> rowValues);

    C buildContext(String email, Company company);

    ImportValidator<T, C> getValidator();

    ImportProcessor<T, C> getImportProcessor();

    default Object buildReferenceData(C context) {
        return null;
    }
}
