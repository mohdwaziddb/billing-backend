package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.ReminderLog;
import com.billing.entity.enums.InvoiceStatus;
import com.billing.entity.enums.ReminderChannel;
import com.billing.entity.enums.ReminderStatus;
import com.billing.dto.PageResponse;
import com.billing.dto.reminder.OverdueCustomerResponse;
import com.billing.dto.reminder.ReminderHistoryResponse;
import com.billing.dto.reminder.ReminderSendRequest;
import com.billing.exception.BadRequestException;
import com.billing.repository.CustomerRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.ReminderLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final AccessControlService accessControlService;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final CustomerService customerService;

    @Value("${app.reminder.mock-mode:false}")
    private boolean reminderMockMode;

    @Transactional(readOnly = true)
    public PageResponse<OverdueCustomerResponse> getOverdueCustomers(String email, String search, BigDecimal minBalance, Integer overdueDays, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        Pageable pageable = pageRequest(page, size);
        String normalizedSearch = normalizeSearch(search);
        BigDecimal scaledMinBalance = scaleNullable(minBalance);

        if (overdueDays == null) {
            Page<OverdueCustomerResponse> responsePage = customerRepository
                    .findOutstandingPageByCompanyWithFilters(company, normalizedSearch, scaledMinBalance, pageable)
                    .map(customer -> toOverdueCustomerResponse(company, customer, LocalDate.now()))
                    .map(item -> item.orElse(null));
            List<OverdueCustomerResponse> records = responsePage.getContent().stream()
                    .filter(Objects::nonNull)
                    .toList();
            return PageResponse.from(new PageImpl<>(records, pageable, responsePage.getTotalElements()));
        }

        List<OverdueCustomerResponse> allRecords = buildOverdueCustomers(company, normalizedSearch, scaledMinBalance, overdueDays);
        int fromIndex = Math.min((int) pageable.getOffset(), allRecords.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), allRecords.size());
        return PageResponse.from(new PageImpl<>(allRecords.subList(fromIndex, toIndex), pageable, allRecords.size()));
    }

    @Transactional
    public ReminderHistoryResponse sendReminder(String email, ReminderSendRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerService.getCustomerOrThrow(company, request.getCustomerId());

        if (customer.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Reminder can only be sent to customers with outstanding balance");
        }

        String message = buildReminderMessage(customer, company);
        ReminderStatus status = determineStatus(request.getChannel());

        ReminderLog reminderLog = ReminderLog.builder()
                .company(company)
                .customer(customer)
                .amount(scale(customer.getCurrentBalance()))
                .message(message)
                .channel(request.getChannel())
                .status(status)
                .build();

        ReminderLog savedLog = reminderLogRepository.save(reminderLog);
        return toHistoryResponse(savedLog);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReminderHistoryResponse> history(String email, Long customerId, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerService.getCustomerOrThrow(company, customerId);
        return PageResponse.from(reminderLogRepository.findByCompanyAndCustomerOrderByCreatedAtDesc(company, customer, pageRequest(page, size))
                .map(this::toHistoryResponse));
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void prepareDailyDueCustomerList() {
        long dueCustomerCount = customerRepository.findAll().stream()
                .filter(customer -> customer.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0)
                .count();
        log.info("Prepared {} due customers for reminder review", dueCustomerCount);
    }

    private List<OverdueCustomerResponse> buildOverdueCustomers(Company company, String search, BigDecimal minBalance, Integer overdueDays) {
        LocalDate today = LocalDate.now();

        return customerRepository.findByCompanyAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(company, BigDecimal.ZERO).stream()
                .map(customer -> toOverdueCustomerResponse(company, customer, today).orElse(null))
                .filter(Objects::nonNull)
                .filter(item -> search == null || matchesSearch(item, search))
                .filter(item -> minBalance == null || item.getCurrentBalance().compareTo(minBalance) >= 0)
                .filter(item -> overdueDays == null || item.getOverdueDays() >= overdueDays)
                .sorted(Comparator.comparing(OverdueCustomerResponse::getCurrentBalance).reversed())
                .toList();
    }

    private java.util.Optional<OverdueCustomerResponse> toOverdueCustomerResponse(Company company, Customer customer, LocalDate today) {
        List<Invoice> outstandingInvoices = invoiceRepository.findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(company, customer).stream()
                .filter(invoice -> invoice.getPaymentStatus() != InvoiceStatus.PAID)
                .filter(invoice -> invoice.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (outstandingInvoices.isEmpty()) {
            return java.util.Optional.empty();
        }

        LocalDate oldestOutstandingDate = outstandingInvoices.stream()
                .map(Invoice::getInvoiceDate)
                .min(LocalDate::compareTo)
                .orElse(today);
        int customerOverdueDays = (int) ChronoUnit.DAYS.between(oldestOutstandingDate, today);

        ReminderLog latestReminder = reminderLogRepository.findFirstByCompanyAndCustomerOrderByCreatedAtDesc(company, customer)
                .orElse(null);

        return java.util.Optional.of(OverdueCustomerResponse.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .mobile(customer.getMobile())
                .email(customer.getEmail())
                .currentBalance(scale(customer.getCurrentBalance()))
                .overdueDays(customerOverdueDays)
                .oldestOutstandingInvoiceDate(oldestOutstandingDate)
                .lastReminderAt(latestReminder != null ? latestReminder.getCreatedAt() : null)
                .lastReminderStatus(latestReminder != null ? latestReminder.getStatus().name() : null)
                .build());
    }

    private ReminderStatus determineStatus(ReminderChannel channel) {
        if (reminderMockMode || channel == ReminderChannel.MOCK) {
            return ReminderStatus.SENT;
        }
        return ReminderStatus.PENDING;
    }

    private String buildReminderMessage(Customer customer, Company company) {
        return String.format(
                Locale.ENGLISH,
                "Dear %s, your outstanding balance is ₹%s. Please clear your dues. - %s",
                customer.getName(),
                scale(customer.getCurrentBalance()).toPlainString(),
                company.getName()
        );
    }

    private ReminderHistoryResponse toHistoryResponse(ReminderLog logEntry) {
        return ReminderHistoryResponse.builder()
                .id(logEntry.getId())
                .amount(scale(logEntry.getAmount()))
                .message(logEntry.getMessage())
                .channel(logEntry.getChannel().name())
                .status(logEntry.getStatus().name())
                .createdAt(logEntry.getCreatedAt())
                .createdBy(logEntry.getCreatedBy())
                .build();
    }

    private boolean matchesSearch(OverdueCustomerResponse item, String search) {
        return Stream.of(item.getCustomerName(), item.getMobile(), item.getEmail())
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ENGLISH))
                .anyMatch(value -> value.contains(search));
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ENGLISH);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleNullable(BigDecimal value) {
        return value == null ? null : scale(value);
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }
}
