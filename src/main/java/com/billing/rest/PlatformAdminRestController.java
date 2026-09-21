package com.billing.rest;

import com.billing.service.NotificationSettingsService;
import com.billing.service.PlatformAdminService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform-admin")
@RequiredArgsConstructor
@PreAuthorize("principal instanceof T(com.billing.security.PlatformAdminPrincipal)")
public class PlatformAdminRestController {

    private final PlatformAdminService platformAdminService;
    private final NotificationSettingsService notificationSettingsService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.dashboard()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies")
    public ResponseEntity<?> companies(@RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.companies(param)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/overview")
    public ResponseEntity<?> companyOverview(@RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.companyOverview(param)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies")
    public ResponseEntity<?> createCompany(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.createCompany(param)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/activate")
    public ResponseEntity<?> activateCompany(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.activateCompany(companyId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/deactivate")
    public ResponseEntity<?> deactivateCompany(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.deactivateCompany(companyId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/chatbot/enable")
    public ResponseEntity<?> enableCompanyChatbot(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.setCompanyChatbotEnabled(companyId, true)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/chatbot/disable")
    public ResponseEntity<?> disableCompanyChatbot(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.setCompanyChatbotEnabled(companyId, false)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyId}")
    public ResponseEntity<?> companyDetails(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.companyDetails(companyId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyId}/communication/email-settings")
    public ResponseEntity<?> emailSettings(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.emailSettingsForCompany(companyId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/communication/email-settings")
    public ResponseEntity<?> createEmailSettings(@PathVariable Long companyId, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveEmailSettingsForCompany(param, companyId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/companies/{companyId}/communication/email-settings/{id}")
    public ResponseEntity<?> updateEmailSettings(@PathVariable Long companyId, @PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            id = DataTypeUtility.getForeignKeyValue(id);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveEmailSettingsForCompany(param, companyId, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/communication/email-settings/test")
    public ResponseEntity<?> testEmailSettings(@PathVariable Long companyId, @RequestBody(required = false) Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            if (param == null) {
                param = new HashMap<>();
            }
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestEmailForCompany(param, companyId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyId}/communication/sms-settings")
    public ResponseEntity<?> smsSettings(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.smsSettingsForCompany(companyId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyId}/communication/sms-settings/providers")
    public ResponseEntity<?> smsProviders(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.smsProviderMetadata()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/communication/sms-settings")
    public ResponseEntity<?> createSmsSettings(@PathVariable Long companyId, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveSmsSettingsForCompany(param, companyId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/companies/{companyId}/communication/sms-settings/{id}")
    public ResponseEntity<?> updateSmsSettings(@PathVariable Long companyId, @PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            id = DataTypeUtility.getForeignKeyValue(id);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveSmsSettingsForCompany(param, companyId, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/communication/sms-settings/test")
    public ResponseEntity<?> testSmsSettings(@PathVariable Long companyId, @RequestBody(required = false) Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            if (param == null) {
                param = new HashMap<>();
            }
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestSmsForCompany(param, companyId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyId}/communication/whatsapp-settings")
    public ResponseEntity<?> whatsAppSettings(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.whatsAppSettingsForCompany(companyId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyId}/communication/whatsapp-settings/providers")
    public ResponseEntity<?> whatsAppProviders(@PathVariable Long companyId) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.whatsAppProviderMetadata()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/communication/whatsapp-settings")
    public ResponseEntity<?> createWhatsAppSettings(@PathVariable Long companyId, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveWhatsAppSettingsForCompany(param, companyId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/companies/{companyId}/communication/whatsapp-settings/{id}")
    public ResponseEntity<?> updateWhatsAppSettings(@PathVariable Long companyId, @PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            id = DataTypeUtility.getForeignKeyValue(id);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveWhatsAppSettingsForCompany(param, companyId, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyId}/communication/whatsapp-settings/test")
    public ResponseEntity<?> testWhatsAppSettings(@PathVariable Long companyId, @RequestBody(required = false) Map<String, Object> param, Authentication authentication) {
        try {
            companyId = DataTypeUtility.getForeignKeyValue(companyId);
            if (param == null) {
                param = new HashMap<>();
            }
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestWhatsAppForCompany(param, companyId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<?> settings() {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.settings()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Object> param, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.updateSettings(param)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

