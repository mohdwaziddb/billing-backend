package com.billing.dto.dataport;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ImportResult {
    private int importedRecords;
    private int failedRecords;
    // Kaunsi row kyun import nahi hui (save ke time service ne mana kiya) — user ko dikhane ke liye.
    @Builder.Default
    private List<RowFailure> failures = List.of();

    public record RowFailure(Integer rowNumber, String message) {
    }
}
