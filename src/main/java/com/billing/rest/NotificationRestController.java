package com.billing.rest;

import com.billing.service.NotificationService;
import com.billing.service.NotificationSettingsService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationRestController {

    private final NotificationService notificationService;
    private final NotificationSettingsService notificationSettingsService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @PostMapping("/send")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "EMAIL_SEND")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationService.sendNotification(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/send-email")
    @com.billing.security.RequiresPermission(menu = "EMAIL_TEMPLATES", action = "EMAIL_SEND")
    public ResponseEntity<?> sendEmail(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationService.sendEmail(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/send-sms")
    @com.billing.security.RequiresPermission(menu = "SMS_TEMPLATES", action = "SMS_SEND")
    public ResponseEntity<?> sendSms(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationService.sendSms(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/send-whatsapp")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "WHATSAPP_SEND")
    public ResponseEntity<?> sendWhatsApp(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationService.sendWhatsApp(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/logs")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    public ResponseEntity<?> logs(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationService.logs(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/email-settings")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> emailSettings(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.emailSettings(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/email-settings")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "ADD")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> createEmailSettings(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveEmailSettings(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/email-settings/{id}")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "EDIT")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> saveEmailSettings(@PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            id = DataTypeUtility.getForeignKeyValue(id);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveEmailSettings(param, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/email-settings/test")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> sendTestEmail(@RequestBody(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestEmail(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/sms-settings")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> smsSettings(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.smsSettings(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/sms-settings/providers")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> smsProviders(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.smsProviderMetadata()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/sms-settings")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "ADD")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> createSmsSettings(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveSmsSettings(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/sms-settings/{id}")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "EDIT")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> saveSmsSettings(@PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            id = DataTypeUtility.getForeignKeyValue(id);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveSmsSettings(param, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/sms-settings/test")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> sendTestSms(@RequestBody(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestSms(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/whatsapp-settings")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> whatsAppSettings(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.whatsAppSettings(authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/whatsapp-settings/providers")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> whatsAppProviders(Authentication authentication) {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.whatsAppProviderMetadata()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/whatsapp-settings")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "ADD")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> createWhatsAppSettings(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveWhatsAppSettings(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/whatsapp-settings/{id}")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "EDIT")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> saveWhatsAppSettings(@PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            id = DataTypeUtility.getForeignKeyValue(id);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveWhatsAppSettings(param, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/whatsapp-settings/test")
    @com.billing.security.RequiresPermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<?> sendTestWhatsApp(@RequestBody(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestWhatsApp(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

