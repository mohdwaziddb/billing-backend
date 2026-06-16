package com.billing.dto.dataport;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImportResult {
    private int importedRecords;
    private int failedRecords;
}
