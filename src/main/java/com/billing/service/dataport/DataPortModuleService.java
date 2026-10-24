package com.billing.service.dataport;

import com.billing.dto.dataport.DataPortPreviewResponse;
import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.exception.BadRequestException;
import com.billing.service.AccessControlService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Saare DataPort modules ka common flow: sample -> upload preview ->
 * edit + revalidate -> import. Module sirf apne business rules aur save
 * likhta hai; format checks yahan ek jagah.
 */
@Service
public class DataPortModuleService {

    static final int MAX_ROWS = 5000;

    private final AccessControlService accessControlService;
    private final ExcelReaderService excelReaderService;
    private final Map<String, DataPortModule<?>> modules = new LinkedHashMap<>();

    public DataPortModuleService(AccessControlService accessControlService,
                                 ExcelReaderService excelReaderService,
                                 List<DataPortModule<?>> dataPortModules) {
        this.accessControlService = accessControlService;
        this.excelReaderService = excelReaderService;
        dataPortModules.stream()
                .sorted(Comparator.comparingInt(DataPortModule::order))
                .forEach(module -> modules.put(module.key(), module));
    }

    public List<DataPortModule<?>> modules(Predicate<DataPortModule<?>> visible) {
        return modules.values().stream().filter(visible).toList();
    }

    public DataPortModule<?> module(String key) {
        DataPortModule<?> module = modules.get(key == null ? "" : key.trim().toLowerCase());
        if (module == null) {
            throw new BadRequestException("Unsupported DataPort module");
        }
        return module;
    }

    public ExcelReaderService.SampleFile sample(String key) {
        DataPortModule<?> module = module(key);
        return excelReaderService.buildSampleWorkbook(module);
    }

    public DataPortPreviewResponse<DataPortRow> preview(String email, String key, MultipartFile file) {
        DataPortModule<?> module = module(key);
        List<String> headers = module.fields().stream().map(DataPortField::excelHeader).toList();
        List<Map<String, String>> rawRows = excelReaderService.readRows(file, headers);
        if (rawRows.isEmpty()) {
            throw new BadRequestException("The uploaded file has no data rows. Fill the sample sheet and upload again.");
        }
        List<DataPortRow> rows = new ArrayList<>();
        for (int index = 0; index < rawRows.size(); index++) {
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, String> raw = rawRows.get(index);
            for (DataPortField field : module.fields()) {
                values.put(field.key(), raw.getOrDefault(field.excelHeader(), ""));
            }
            // Excel row number (header = 1) — user ko file me wahi row mil jaye.
            rows.add(new DataPortRow(index + 2, values));
        }
        return validate(email, module, rows);
    }

    public DataPortPreviewResponse<DataPortRow> revalidate(String email, String key, List<DataPortRow> rows) {
        return validate(email, module(key), rows);
    }

    public ImportResult importRows(String email, String key, List<DataPortRow> rows) {
        DataPortModule<?> module = module(key);
        if (rows == null || rows.isEmpty()) {
            throw new BadRequestException("No rows available for import");
        }
        Company company = accessControlService.getCurrentCompany(email);
        // Client ki "valid" baat par bharosa nahi: server par dobara poora validation.
        ValidationRun run = runValidation(email, company, module, rows);
        List<DataPortRow> validRows = run.rows().stream().filter(DataPortRow::isValid).toList();
        int invalid = run.rows().size() - validRows.size();
        if (validRows.isEmpty()) {
            return ImportResult.builder().importedRecords(0).failedRecords(invalid).build();
        }
        ImportResult result = importWith(module, email, company, validRows, run.context());
        return ImportResult.builder()
                .importedRecords(result.getImportedRecords())
                .failedRecords(result.getFailedRecords() + invalid)
                .failures(result.getFailures())
                .build();
    }

    private DataPortPreviewResponse<DataPortRow> validate(String email, DataPortModule<?> module, List<DataPortRow> rows) {
        Company company = accessControlService.getCurrentCompany(email);
        List<DataPortRow> validated = runValidation(email, company, module, rows).rows();
        int validCount = (int) validated.stream().filter(DataPortRow::isValid).count();
        return DataPortPreviewResponse.<DataPortRow>builder()
                .rows(validated)
                .totalRows(validated.size())
                .validRows(validCount)
                .invalidRows(validated.size() - validCount)
                .build();
    }

    private record ValidationRun(List<DataPortRow> rows, Object context) {
    }

    @SuppressWarnings("unchecked")
    private <C> ValidationRun runValidation(String email, Company company, DataPortModule<C> module, List<DataPortRow> input) {
        if (input == null) {
            input = List.of();
        }
        if (input.size() > MAX_ROWS) {
            throw new BadRequestException("Maximum " + MAX_ROWS + " rows can be imported at once. Split the file and try again.");
        }
        List<DataPortRow> rows = new ArrayList<>();
        int fallbackNumber = 1;
        for (DataPortRow source : input) {
            DataPortRow row = new DataPortRow(source.getRowNumber() == null ? fallbackNumber : source.getRowNumber(), Map.of());
            fallbackNumber = row.getRowNumber() + 1;
            for (DataPortField field : module.fields()) {
                String value = source.getValues() == null ? null : source.getValues().get(field.key());
                row.getValues().put(field.key(), value == null ? "" : value.trim());
            }
            checkFormats(row, module.fields());
            rows.add(row);
        }
        C context = module.buildContext(email, company);
        module.validate(rows, context);
        if (module.groupField() != null) {
            failWholeGroup(rows, module.groupField());
        }
        rows.forEach(row -> row.setValid(row.getValidationErrors().isEmpty()));
        return new ValidationRun(rows, context);
    }

    @SuppressWarnings("unchecked")
    private <C> ImportResult importWith(DataPortModule<C> module, String email, Company company, List<DataPortRow> rows, Object context) {
        return module.importRows(email, company, rows, (C) context);
    }

    private void checkFormats(DataPortRow row, List<DataPortField> fields) {
        for (DataPortField field : fields) {
            String value = row.value(field.key());
            if (value.isEmpty()) {
                if (field.required()) {
                    row.error(field.key(), field.header() + " is required");
                }
                continue;
            }
            switch (field.type()) {
                case NUMBER -> {
                    if (DataPortValues.integer(value) == null) {
                        row.error(field.key(), field.header() + " must be a whole number");
                    }
                }
                case DECIMAL -> {
                    if (DataPortValues.decimal(value) == null) {
                        row.error(field.key(), field.header() + " must be a number");
                    }
                }
                case DATE -> {
                    if (DataPortValues.date(value) == null) {
                        row.error(field.key(), field.header() + " must be a date like 15-10-2026");
                    }
                }
                case BOOLEAN -> {
                    try {
                        Boolean parsed = DataPortValues.bool(value);
                        row.getValues().put(field.key(), Boolean.TRUE.equals(parsed) ? "Yes" : "No");
                    } catch (IllegalArgumentException ex) {
                        row.error(field.key(), field.header() + " must be Yes or No");
                    }
                }
                case CHOICE -> {
                    Optional<String> match = field.options().stream()
                            .filter(option -> DataPortValues.key(option).equals(DataPortValues.key(value)))
                            .findFirst();
                    if (match.isPresent()) {
                        row.getValues().put(field.key(), match.get());
                    } else {
                        row.error(field.key(), field.header() + " must be one of: " + String.join(", ", field.options()));
                    }
                }
                default -> {
                    // TEXT: koi format check nahi
                }
            }
        }
    }

    /** Invoice jaise multi-row record: ek row kharab ho to poora record ruk jaye (aadha invoice na bane). */
    private void failWholeGroup(List<DataPortRow> rows, String groupField) {
        Map<String, List<DataPortRow>> groups = new LinkedHashMap<>();
        for (DataPortRow row : rows) {
            String groupKey = DataPortValues.key(row.value(groupField));
            if (!groupKey.isEmpty()) {
                groups.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(row);
            }
        }
        for (List<DataPortRow> group : groups.values()) {
            List<Integer> badRows = group.stream()
                    .filter(row -> !row.getValidationErrors().isEmpty())
                    .map(DataPortRow::getRowNumber)
                    .toList();
            if (badRows.isEmpty()) {
                continue;
            }
            for (DataPortRow row : group) {
                if (row.getValidationErrors().isEmpty()) {
                    row.error(groupField, "Row " + badRows.get(0) + " of the same record has errors; fix it to import this record");
                }
            }
        }
    }
}
