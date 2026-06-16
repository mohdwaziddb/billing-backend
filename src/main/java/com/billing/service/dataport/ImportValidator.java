package com.billing.service.dataport;

import com.billing.dto.dataport.ValidatableImportRow;

import java.util.List;

public interface ImportValidator<T extends ValidatableImportRow, C> {

    List<T> validate(List<T> rows, C context);
}
