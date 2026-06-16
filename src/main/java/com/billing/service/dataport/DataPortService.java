package com.billing.service.dataport;

import com.billing.dto.dataport.DataPortPreviewResponse;
import com.billing.dto.dataport.ImportResult;
import com.billing.dto.dataport.ValidatableImportRow;
import com.billing.entity.Company;
import com.billing.exception.BadRequestException;
import com.billing.service.AccessControlService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataPortService {

    private final AccessControlService accessControlService;
    private final ExcelReaderService excelReaderService;
    private final PreviewBuilder previewBuilder;
    private final Map<String, DataPortDefinition<?, ?>> definitions;

    public DataPortService(AccessControlService accessControlService,
                           ExcelReaderService excelReaderService,
                           PreviewBuilder previewBuilder,
                           List<DataPortDefinition<?, ?>> dataPortDefinitions) {
        this.accessControlService = accessControlService;
        this.excelReaderService = excelReaderService;
        this.previewBuilder = previewBuilder;
        this.definitions = new LinkedHashMap<>();
        for (DataPortDefinition<?, ?> definition : dataPortDefinitions) {
            this.definitions.put(definition.getModuleKey(), definition);
        }
    }

    public SampleFile downloadSample(String moduleKey) {
        DataPortDefinition<?, ?> definition = getDefinition(moduleKey);
        byte[] fileContent = excelReaderService.buildSampleWorkbook(
                definition.getSheetName(),
                definition.getColumns(),
                definition.getSampleRows()
        );
        return new SampleFile(definition.getSampleFileName(), fileContent);
    }

    public <T extends ValidatableImportRow> DataPortPreviewResponse<T> preview(String email, String moduleKey, MultipartFile file) {
        Company company = accessControlService.getCurrentCompany(email);
        return previewInternal(email, company, moduleKey, file);
    }

    public <T extends ValidatableImportRow> DataPortPreviewResponse<T> previewRows(String email, String moduleKey, List<T> rows) {
        Company company = accessControlService.getCurrentCompany(email);
        return previewRowsInternal(email, company, moduleKey, rows);
    }

    public <T extends ValidatableImportRow> ImportResult importRows(String email, String moduleKey, List<T> rows) {
        Company company = accessControlService.getCurrentCompany(email);
        DataPortDefinition<T, Object> definition = getTypedDefinition(moduleKey);
        if (rows == null || rows.isEmpty()) {
            throw new BadRequestException("No rows available for import");
        }

        Object context = definition.buildContext(email, company);
        List<T> validatedRows = definition.getValidator().validate(rows, context);
        List<T> validRows = validatedRows.stream().filter(ValidatableImportRow::isValid).toList();
        int importedRecords = validRows.isEmpty() ? 0 : definition.getImportProcessor().process(email, company, validRows, context);

        return ImportResult.builder()
                .importedRecords(importedRecords)
                .failedRecords(validatedRows.size() - importedRecords)
                .build();
    }

    private <T extends ValidatableImportRow> DataPortPreviewResponse<T> previewInternal(String email,
                                                                                        Company company,
                                                                                        String moduleKey,
                                                                                        MultipartFile file) {
        DataPortDefinition<T, Object> definition = getTypedDefinition(moduleKey);
        List<Map<String, String>> rawRows = excelReaderService.readRows(file, definition.getColumns());
        List<T> rows = new ArrayList<>();
        for (int index = 0; index < rawRows.size(); index++) {
            rows.add(definition.mapRow(index + 1, rawRows.get(index)));
        }
        return buildPreview(email, company, definition, rows);
    }

    private <T extends ValidatableImportRow> DataPortPreviewResponse<T> previewRowsInternal(String email,
                                                                                            Company company,
                                                                                            String moduleKey,
                                                                                            List<T> rows) {
        DataPortDefinition<T, Object> definition = getTypedDefinition(moduleKey);
        return buildPreview(email, company, definition, rows);
    }

    private <T extends ValidatableImportRow> DataPortPreviewResponse<T> buildPreview(String email,
                                                                                     Company company,
                                                                                     DataPortDefinition<T, Object> definition,
                                                                                     List<T> rows) {
        Object context = definition.buildContext(email, company);
        List<T> validatedRows = definition.getValidator().validate(rows, context);
        return previewBuilder.build(validatedRows, definition.buildReferenceData(context));
    }

    private DataPortDefinition<?, ?> getDefinition(String moduleKey) {
        DataPortDefinition<?, ?> definition = definitions.get(moduleKey);
        if (definition == null) {
            throw new BadRequestException("Unsupported DataPort module");
        }
        return definition;
    }

    @SuppressWarnings("unchecked")
    private <T extends ValidatableImportRow, C> DataPortDefinition<T, C> getTypedDefinition(String moduleKey) {
        return (DataPortDefinition<T, C>) getDefinition(moduleKey);
    }

    public record SampleFile(String fileName, byte[] content) {
    }
}
