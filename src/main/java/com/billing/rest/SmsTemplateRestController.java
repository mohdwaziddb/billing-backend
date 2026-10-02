package com.billing.rest;

import com.billing.service.EmailTemplateService;
import com.billing.service.SmsTemplateService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sms-templates")
@RequiredArgsConstructor
public class SmsTemplateRestController {

    private final SmsTemplateService smsTemplateService;
    private final EmailTemplateService emailTemplateService;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "SMS_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> page(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", smsTemplateService.page(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/active")
    @com.billing.security.RequiresPermission(menu = "SMS_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> active(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", smsTemplateService.activeTemplates(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/variables")
    @com.billing.security.RequiresPermission(menu = "SMS_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> variables(HttpServletRequest req) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.variables()), HttpStatus.OK);
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "SMS_TEMPLATES", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", smsTemplateService.create(param, authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/{templateId}")
    @com.billing.security.RequiresPermission(menu = "SMS_TEMPLATES", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long templateId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        templateId = DataTypeUtility.getForeignKeyValue(templateId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", smsTemplateService.update(param, templateId, authentication.getName())), HttpStatus.OK);
    }

    @DeleteMapping("/{templateId}")
    @com.billing.security.RequiresPermission(menu = "SMS_TEMPLATES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long templateId) {
        templateId = DataTypeUtility.getForeignKeyValue(templateId);
        smsTemplateService.delete(authentication.getName(), templateId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
    }

    @PostMapping("/{templateId}/preview")
    @com.billing.security.RequiresPermission(menu = "SMS_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> preview(Authentication authentication, @PathVariable Long templateId, @RequestBody(required = false) Map<String, Object> param, HttpServletRequest request) {
        if (param == null) {
            param = Map.of();
        }
        param = SanitizeData.sanitizeMapObj(param);
        templateId = DataTypeUtility.getForeignKeyValue(templateId);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", smsTemplateService.preview(param, authentication.getName(), templateId)), HttpStatus.OK);
    }
}
