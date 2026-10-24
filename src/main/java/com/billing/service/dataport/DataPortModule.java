package com.billing.service.dataport;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;

import java.util.List;

/**
 * Ek DataPort module (Customers, Products, Opening Stock ...).
 *
 * Common checks (required, number/date/yes-no/choice format) DataPortModuleService
 * pehle hi kar deta hai; module sirf business rules (duplicate, master
 * exist karta hai ya nahi) validate karta hai aur save ke liye wahi service
 * call karta hai jo UI use karta hai — stock/ledger/GST/audit same rahe.
 */
public interface DataPortModule<C> {

    /** URL key: /api/v1/data-ports/{key}, frontend /data-port/{key}. */
    String key();

    /** Role permission menu (VIEW = page + sample, ADD = upload/import). */
    String menuCode();

    String title();

    String description();

    /** Hub par kis kram me dikhe (pehle masters, phir transactions). */
    int order();

    List<DataPortField> fields();

    List<List<String>> sampleRows();

    /** Instructions sheet ki extra lines (kram, dependency, rules). */
    default List<String> notes() {
        return List.of();
    }

    /** Ek hi record kai rows me ho (invoice items) to group column; warna null. */
    default String groupField() {
        return null;
    }

    C buildContext(String email, Company company);

    /** Business validation; errors {@link DataPortRow#error} se lagao. */
    void validate(List<DataPortRow> rows, C context);

    /** Sirf valid rows aati hain. Har record apni transaction me — ek fail ho to baaki bache. */
    ImportResult importRows(String email, Company company, List<DataPortRow> validRows, C context);
}
