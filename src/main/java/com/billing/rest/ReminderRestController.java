package com.billing.rest;

import com.billing.service.ReminderService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderRestController {

    private final ReminderService reminderService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping("/overdue-customers")
    @com.billing.security.RequiresPermission(menu = "OUTSTANDING", action = "VIEW")
    public ResponseEntity<?> overdueCustomers(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req, HttpServletResponse res) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", reminderService.getOverdueCustomers(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/send")
    @com.billing.security.RequiresPermission(menu = "OUTSTANDING", action = "ADD")
    public ResponseEntity<?> sendReminder(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", reminderService.sendReminder(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/customer/{customerId}/history")
    @com.billing.security.RequiresPermission(menu = "OUTSTANDING", action = "VIEW")
    public ResponseEntity<?> history(Authentication authentication, @PathVariable Long customerId, @RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            customerId = DataTypeUtility.getForeignKeyValue(customerId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", reminderService.history(param, authentication.getName(), customerId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

