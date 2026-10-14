package com.billing.rest;

import com.billing.dto.notification.NotificationChannelType;
import com.billing.security.RequiresPermission;
import com.billing.service.CommunicationHistoryService;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/communication-history")
@RequiredArgsConstructor
public class CommunicationHistoryRestController {

    private final CommunicationHistoryService communicationHistoryService;

    @GetMapping("/mail")
    @RequiresPermission(menu = "MAIL_HISTORY", action = "VIEW")
    public ResponseEntity<?> mailHistory(@RequestParam(required = false) Map<String, Object> param, Authentication authentication) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                communicationHistoryService.mailHistory(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/sms")
    @RequiresPermission(menu = "SMS_HISTORY", action = "VIEW")
    public ResponseEntity<?> smsHistory(@RequestParam(required = false) Map<String, Object> param, Authentication authentication) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                communicationHistoryService.channelHistory(param, authentication.getName(), NotificationChannelType.SMS)), HttpStatus.OK);
    }

    @GetMapping("/whatsapp")
    @RequiresPermission(menu = "WHATSAPP_HISTORY", action = "VIEW")
    public ResponseEntity<?> whatsappHistory(@RequestParam(required = false) Map<String, Object> param, Authentication authentication) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully",
                communicationHistoryService.channelHistory(param, authentication.getName(), NotificationChannelType.WHATSAPP)), HttpStatus.OK);
    }
}
