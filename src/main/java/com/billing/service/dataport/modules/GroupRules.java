package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.service.dataport.DataPortValues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Multi-row records (purchase / invoice) ke common rules. */
final class GroupRules {

    private GroupRules() {
    }

    /** Group key (lowercase) -> rows. Blank key wali rows shamil nahi (wo akeli record hain). */
    static Map<String, List<DataPortRow>> groups(List<DataPortRow> rows, String groupField) {
        Map<String, List<DataPortRow>> groups = new LinkedHashMap<>();
        for (DataPortRow row : rows) {
            String key = DataPortValues.key(row.value(groupField));
            if (!key.isEmpty()) {
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            }
        }
        return groups;
    }

    /**
     * Header columns (date, customer, supplier ...) poore record me ek jaise hon.
     * Baad wali row khaali chhodi ho to pehli row ki value le lete hain.
     */
    static void sameHeaderValues(List<DataPortRow> rows, String groupField, List<String> headerFields) {
        for (List<DataPortRow> group : groups(rows, groupField).values()) {
            DataPortRow first = group.get(0);
            for (DataPortRow row : group.subList(1, group.size())) {
                for (String field : headerFields) {
                    String expected = first.value(field);
                    String actual = row.value(field);
                    if (actual.isEmpty()) {
                        row.getValues().put(field, expected);
                        String error = row.getValidationErrors().get(field);
                        if (error != null && error.endsWith(" is required") && !expected.isEmpty()) {
                            row.getValidationErrors().remove(field);
                        }
                    } else if (!DataPortValues.key(actual).equals(DataPortValues.key(expected))) {
                        row.error(field, "Must be the same as row " + first.getRowNumber() + " of this record (" + expected + ")");
                    }
                }
            }
        }
    }
}
