package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.reminder.OverdueCustomerResponse;
import com.billing.saas.dto.reminder.ReminderHistoryResponse;
import com.billing.saas.dto.reminder.ReminderSendRequest;
import com.billing.saas.service.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping("/overdue-customers")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<List<OverdueCustomerResponse>>> overdueCustomers(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) Integer overdueDays
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Overdue customers fetched successfully",
                reminderService.getOverdueCustomers(authentication.getName(), search, minBalance, overdueDays)
        ));
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<ReminderHistoryResponse>> sendReminder(
            Authentication authentication,
            @Valid @RequestBody ReminderSendRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Reminder sent successfully",
                reminderService.sendReminder(authentication.getName(), request)
        ));
    }

    @GetMapping("/customer/{customerId}/history")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<List<ReminderHistoryResponse>>> history(
            Authentication authentication,
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Reminder history fetched successfully",
                reminderService.history(authentication.getName(), customerId)
        ));
    }
}
