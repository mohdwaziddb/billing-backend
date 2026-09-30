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

    @PostMapping("/companies/{companyCode}/activate")
    public ResponseEntity<?> activateCompany(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.activateCompany(companyCode)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/deactivate")
    public ResponseEntity<?> deactivateCompany(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.deactivateCompany(companyCode)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/chatbot/enable")
    public ResponseEntity<?> enableCompanyChatbot(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.setCompanyChatbotEnabled(companyCode, true)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/chatbot/disable")
    public ResponseEntity<?> disableCompanyChatbot(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.setCompanyChatbotEnabled(companyCode, false)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyCode}")
    public ResponseEntity<?> companyDetails(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformAdminService.companyDetails(companyCode)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyCode}/communication/email-settings")
    public ResponseEntity<?> emailSettings(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.emailSettingsForCompany(companyCode)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/communication/email-settings")
    public ResponseEntity<?> createEmailSettings(@PathVariable String companyCode, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveEmailSettingsForCompany(param, companyCode, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/companies/{companyCode}/communication/email-settings/{id}")
    public ResponseEntity<?> updateEmailSettings(@PathVariable String companyCode, @PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            id = DataTypeUtility.getForeignKeyValue(id);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveEmailSettingsForCompany(param, companyCode, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/communication/email-settings/test")
    public ResponseEntity<?> testEmailSettings(@PathVariable String companyCode, @RequestBody(required = false) Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            if (param == null) {
                param = new HashMap<>();
            }
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestEmailForCompany(param, companyCode, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyCode}/communication/sms-settings")
    public ResponseEntity<?> smsSettings(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.smsSettingsForCompany(companyCode)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyCode}/communication/sms-settings/providers")
    public ResponseEntity<?> smsProviders(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.smsProviderMetadata()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/communication/sms-settings")
    public ResponseEntity<?> createSmsSettings(@PathVariable String companyCode, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveSmsSettingsForCompany(param, companyCode, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/companies/{companyCode}/communication/sms-settings/{id}")
    public ResponseEntity<?> updateSmsSettings(@PathVariable String companyCode, @PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            id = DataTypeUtility.getForeignKeyValue(id);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveSmsSettingsForCompany(param, companyCode, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/communication/sms-settings/test")
    public ResponseEntity<?> testSmsSettings(@PathVariable String companyCode, @RequestBody(required = false) Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            if (param == null) {
                param = new HashMap<>();
            }
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestSmsForCompany(param, companyCode, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyCode}/communication/whatsapp-settings")
    public ResponseEntity<?> whatsAppSettings(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.whatsAppSettingsForCompany(companyCode)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/companies/{companyCode}/communication/whatsapp-settings/providers")
    public ResponseEntity<?> whatsAppProviders(@PathVariable String companyCode) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.whatsAppProviderMetadata()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/communication/whatsapp-settings")
    public ResponseEntity<?> createWhatsAppSettings(@PathVariable String companyCode, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveWhatsAppSettingsForCompany(param, companyCode, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/companies/{companyCode}/communication/whatsapp-settings/{id}")
    public ResponseEntity<?> updateWhatsAppSettings(@PathVariable String companyCode, @PathVariable Long id, @RequestBody Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            id = DataTypeUtility.getForeignKeyValue(id);
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.saveWhatsAppSettingsForCompany(param, companyCode, id, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/companies/{companyCode}/communication/whatsapp-settings/test")
    public ResponseEntity<?> testWhatsAppSettings(@PathVariable String companyCode, @RequestBody(required = false) Map<String, Object> param, Authentication authentication) {
        try {
            companyCode = companyCode == null ? null : companyCode.trim();
            if (param == null) {
                param = new HashMap<>();
            }
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", notificationSettingsService.sendTestWhatsAppForCompany(param, companyCode, authentication.getName())), HttpStatus.OK);
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

