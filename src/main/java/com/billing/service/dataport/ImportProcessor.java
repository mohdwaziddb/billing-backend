package com.billing.service.dataport;

import com.billing.dto.dataport.ValidatableImportRow;
import com.billing.entity.Company;

import java.util.List;

public interface ImportProcessor<T extends ValidatableImportRow, C> {

    int process(String email, Company company, List<T> validRows, C context);
}
