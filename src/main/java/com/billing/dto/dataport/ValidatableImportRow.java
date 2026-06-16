package com.billing.dto.dataport;

import java.util.Map;

public interface ValidatableImportRow {

    Integer getRowNumber();

    boolean isValid();

    void setValid(boolean valid);

    Map<String, String> getValidationErrors();

    void setValidationErrors(Map<String, String> validationErrors);
}
