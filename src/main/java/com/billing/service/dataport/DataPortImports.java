package com.billing.service.dataport;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.util.SanitizeData;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Common import loop: har record (ek row, ya same group wali rows) apni
 * service call me save hota hai. Service @Transactional hai, isliye ek record
 * fail ho to sirf wahi rollback — baaki import ho jaate hain aur fail ka
 * reason row number ke saath user ko milta hai.
 *
 * Module ka importRows @Transactional NAHI hona chahiye (warna ek fail
 * poori transaction ko rollback-only kar deta).
 */
public final class DataPortImports {

    private DataPortImports() {
    }

    public static ImportResult perRow(List<DataPortRow> rows, Consumer<DataPortRow> saveOne) {
        return perGroup(rows, null, group -> saveOne.accept(group.get(0)));
    }

    public static ImportResult perGroup(List<DataPortRow> rows, String groupField, Consumer<List<DataPortRow>> saveGroup) {
        Map<String, List<DataPortRow>> groups = new LinkedHashMap<>();
        int solo = 0;
        for (DataPortRow row : rows) {
            String groupKey = groupField == null ? "" : DataPortValues.key(row.value(groupField));
            if (groupKey.isEmpty()) {
                groupKey = "#row-" + (solo++);
            }
            groups.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(row);
        }

        int imported = 0;
        int failed = 0;
        List<ImportResult.RowFailure> failures = new ArrayList<>();
        for (List<DataPortRow> group : groups.values()) {
            try {
                saveGroup.accept(group);
                imported += group.size();
            } catch (RuntimeException ex) {
                failed += group.size();
                String message = friendly(ex);
                for (DataPortRow row : group) {
                    failures.add(new ImportResult.RowFailure(row.getRowNumber(), message));
                }
            }
        }
        return ImportResult.builder().importedRecords(imported).failedRecords(failed).failures(failures).build();
    }

    /** UI controller jaisa hi sanitize karke service ke Map method ko do. */
    public static Map<String, Object> sanitized(Map<String, Object> param) {
        return SanitizeData.sanitizeMapObj(param);
    }

    private static String friendly(RuntimeException ex) {
        if (ex instanceof AccessDeniedException) {
            return "You do not have permission to create this record";
        }
        if (ex instanceof DataIntegrityViolationException) {
            return "Duplicate or invalid value — this record conflicts with existing data";
        }
        return DataPortValues.rootMessage(ex);
    }
}
