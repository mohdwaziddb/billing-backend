package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.reminder.OverdueCustomerResponse;
import com.billing.dto.reminder.ReminderHistoryResponse;
import com.billing.dto.reminder.ReminderSendRequest;
import com.billing.security.RequirePermission;
import com.billing.service.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping("/overdue-customers")
    @RequirePermission(menu = "OUTSTANDING", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<OverdueCustomerResponse>>> overdueCustomers(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) Integer overdueDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Overdue customers fetched successfully",
                reminderService.getOverdueCustomers(authentication.getName(), search, minBalance, overdueDays, page, size)
        ));
    }

    @PostMapping("/send")
    @RequirePermission(menu = "OUTSTANDING", action = "ADD")
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
    @RequirePermission(menu = "OUTSTANDING", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<ReminderHistoryResponse>>> history(
            Authentication authentication,
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Reminder history fetched successfully",
                reminderService.history(authentication.getName(), customerId, page, size)
        ));
    }
}
