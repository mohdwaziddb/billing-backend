package com.billing.rest;

import com.billing.dto.dataport.DataPortRow;
import com.billing.service.PermissionService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortModuleService;
import com.billing.service.dataport.ExcelReaderService;
import com.billing.util.GeneralResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saare DataPort modules ka ek hi controller: /api/v1/data-ports/{module}/...
 * Permission har module ke apne menu code par (Role Permissions page se
 * manage hota hai): VIEW = page + sample, ADD = upload/preview/import.
 *
 * Body sanitize nahi hota: values Excel ka data hai aur har module save ke
 * liye wahi service Map method call karta hai jo UI controller karta hai —
 * wahan SanitizeData lagta hai (DataPortModuleSupport.sanitized).
 */
@RestController
@RequestMapping("/api/v1/data-ports")
@RequiredArgsConstructor
public class DataPortRestController {

    private final DataPortModuleService dataPortModuleService;
    private final PermissionService permissionService;

    @GetMapping("/modules")
    public ResponseEntity<?> modules(Authentication authentication) {
        List<Map<String, Object>> modules = dataPortModuleService
                .modules(module -> permissionService.has(authentication, module.menuCode(), "VIEW"))
                .stream()
                .map(module -> describe(module, authentication))
                .toList();
        return ResponseEntity.ok(new GeneralResponse<>(true, "Successfully", modules));
    }

    @GetMapping("/{module}/sample")
    public ResponseEntity<?> sample(@PathVariable String module, Authentication authentication) {
        require(authentication, module, "VIEW");
        ExcelReaderService.SampleFile sampleFile = dataPortModuleService.sample(module);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(sampleFile.fileName()).build().toString())
                .body(sampleFile.content());
    }

    @PostMapping(value = "/{module}/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(@PathVariable String module, @RequestPart("file") MultipartFile file, Authentication authentication) {
        require(authentication, module, "ADD");
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                dataPortModuleService.preview(authentication.getName(), module, file)), HttpStatus.OK);
    }

    @PostMapping("/{module}/revalidate")
    public ResponseEntity<?> revalidate(@PathVariable String module, @RequestBody Map<String, Object> param, Authentication authentication) {
        require(authentication, module, "ADD");
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                dataPortModuleService.revalidate(authentication.getName(), module, rows(param))), HttpStatus.OK);
    }

    @PostMapping("/{module}/import")
    public ResponseEntity<?> importRows(@PathVariable String module, @RequestBody Map<String, Object> param, Authentication authentication) {
        require(authentication, module, "ADD");
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                dataPortModuleService.importRows(authentication.getName(), module, rows(param))), HttpStatus.OK);
    }

    private void require(Authentication authentication, String moduleKey, String action) {
        DataPortModule<?> module = dataPortModuleService.module(moduleKey);
        if (!permissionService.has(authentication, module.menuCode(), action)) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    private Map<String, Object> describe(DataPortModule<?> module, Authentication authentication) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", module.key());
        data.put("menuCode", module.menuCode());
        data.put("title", module.title());
        data.put("description", module.description());
        data.put("order", module.order());
        data.put("notes", module.notes());
        data.put("groupField", module.groupField());
        data.put("canImport", permissionService.has(authentication, module.menuCode(), "ADD"));
        List<Map<String, Object>> fields = new ArrayList<>();
        for (DataPortField field : module.fields()) {
            Map<String, Object> fieldData = new LinkedHashMap<>();
            fieldData.put("key", field.key());
            fieldData.put("header", field.header());
            fieldData.put("required", field.required());
            fieldData.put("type", field.type().name());
            fieldData.put("options", field.options());
            fieldData.put("hint", field.hint());
            fields.add(fieldData);
        }
        data.put("fields", fields);
        return data;
    }

    private List<DataPortRow> rows(Map<String, Object> param) {
        Object raw = param == null ? null : param.get("rows");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<DataPortRow> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                DataPortRow row = new DataPortRow();
                Object number = map.get("rowNumber");
                row.setRowNumber(number instanceof Number value ? value.intValue() : null);
                Object values = map.get("values");
                if (values instanceof Map<?, ?> valueMap) {
                    Map<String, String> clean = new LinkedHashMap<>();
                    valueMap.forEach((key, value) -> clean.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
                    row.setValues(clean);
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
