package com.billing.rest;

import com.billing.service.EmailTemplateService;
import com.billing.service.NotificationChannelService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/email-templates")
@RequiredArgsConstructor
public class EmailTemplateRestController {

    private final EmailTemplateService emailTemplateService;
    private final NotificationChannelService notificationChannelService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> page(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/active")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> activeTemplates(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.activeTemplates(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/variables")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> variables() {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.variables()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/channels")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> channels(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationChannelService.activeChannels(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{templateId}")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long templateId) {
        try {
            templateId = DataTypeUtility.getForeignKeyValue(templateId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.get(authentication.getName(), templateId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{templateId}")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long templateId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.update(param, DataTypeUtility.getForeignKeyValue(templateId), authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{templateId}")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long templateId) {
        try {
            templateId = DataTypeUtility.getForeignKeyValue(templateId);
            emailTemplateService.delete(authentication.getName(), templateId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/{templateId}/preview")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> preview(Authentication authentication, @PathVariable Long templateId, @RequestBody(required = false) Map<String, Object> param, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            templateId = DataTypeUtility.getForeignKeyValue(templateId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.preview(param, templateId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/send")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "EMAIL_SEND")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.send(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/logs")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> logs(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", emailTemplateService.logs(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

