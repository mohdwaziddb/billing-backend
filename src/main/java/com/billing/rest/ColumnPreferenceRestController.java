package com.billing.rest;

import com.billing.service.UserPreferenceService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/column-preferences")
@RequiredArgsConstructor
public class ColumnPreferenceRestController {

    private final UserPreferenceService userPreferenceService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping("/{tableName}")
    public ResponseEntity<?> get(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, @PathVariable String tableName, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            String tableNameValue = DataTypeUtility.stringValue(tableName);
            if (tableNameValue.length() == 0) {
                tableNameValue = DataTypeUtility.stringValue(param.get("tableName"));
            }
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userPreferenceService.getColumnPreference(param, authentication.getName(), DataTypeUtility.stringValue(tableNameValue))), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{tableName}")
    public ResponseEntity<?> update(@PathVariable String tableName, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            String tableNameValue = DataTypeUtility.stringValue(tableName);
            if (tableNameValue.length() == 0) {
                tableNameValue = DataTypeUtility.stringValue(param.get("tableName"));
            }
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", userPreferenceService.updateColumnPreference(param, authentication.getName(), DataTypeUtility.stringValue(tableNameValue))), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

