package com.billing.service;

import com.billing.dto.payment.PaymentHierarchyNodeResponse;
import com.billing.dto.payment.PaymentHierarchyRecordResponse;
import com.billing.dto.payment.PaymentHierarchyResponse;
import com.billing.entity.Company;
import com.billing.entity.Expense;
import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import com.billing.repository.ExpenseRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentHierarchyService {

    private final AccessControlService accessControlService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentModeMasterService paymentModeMasterService;
    private final AuditNameResolver auditNameResolver;

    @Transactional
    public PaymentHierarchyResponse children(String email,
                                             String nodeType,
                                             String mode,
                                             Integer year,
                                             Integer month,
                                             LocalDate day,
                                             LocalDate startDate,
                                             LocalDate endDate,
                                             Integer financialYear,
                                             Long customerId,
                                             String collectedBy) {
        Company company = accessControlService.getCurrentCompany(email);
        DateRange dateRange = resolveRange(startDate, endDate, financialYear);
        List<Invoice> invoices = filterInvoices(invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company), dateRange, customerId);
        List<Payment> payments = filterPayments(paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company), dateRange, mode, customerId, collectedBy);
        List<Expense> expenses = filterExpenses(expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company), dateRange, customerId);
        paymentModeMasterService.ensureDefaults(company);
        Map<String, String> modeLabels = paymentModeMasterService.activeModeLabels(company);
        String resolvedType = nodeType == null || nodeType.isBlank() ? "company" : nodeType.trim();
        Summary summary = summary(invoices, payments, expenses);

        List<PaymentHierarchyNodeResponse> nodes = switch (resolvedType) {
            case "company" -> rootNodes(summary);
            case "receivable" -> invoiceNodes(invoices, "receivable", false);
            case "outstanding" -> invoiceNodes(invoices.stream()
                    .filter(invoice -> scale(invoice.getBalanceAmount()).compareTo(BigDecimal.ZERO) > 0)
                    .toList(), "outstanding", true);
            case "collected" -> modeNodes(payments, modeLabels);
            case "mode" -> yearNodes(filterByMode(payments, mode), mode);
            case "year" -> monthNodes(filterByMode(payments, mode).stream()
                    .filter(payment -> payment.getPaymentDate() != null && payment.getPaymentDate().getYear() == safeInt(year))
                    .toList(), mode, safeInt(year));
            case "month" -> dayNodes(filterByMode(payments, mode).stream()
                    .filter(payment -> matchesMonth(payment, safeInt(year), safeInt(month)))
                    .toList(), mode, safeInt(year), safeInt(month));
            case "day" -> recordNodes(filterByMode(payments, mode).stream()
                    .filter(payment -> Objects.equals(payment.getPaymentDate(), day))
                    .toList(), mode, day);
            default -> List.of();
        };

        List<PaymentHierarchyRecordResponse> records = "day".equals(resolvedType)
                ? paymentRecords(filterByMode(payments, mode).stream()
                    .filter(payment -> Objects.equals(payment.getPaymentDate(), day))
                    .toList())
                : List.of();

        return PaymentHierarchyResponse.builder()
                .nodeId(resolvedType)
                .nodeType(resolvedType)
                .companyName(company.getName())
                .totalReceivable(summary.totalReceivable())
                .totalCollected(summary.totalCollected())
                .totalOutstanding(summary.totalOutstanding())
                .totalExpense(summary.totalExpense())
                .netRevenue(summary.netRevenue())
                .nodes(nodes)
                .records(records)
                .build();
    }

    @Transactional(readOnly = true)
    public PaymentHierarchyResponse summary(String email,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            Integer financialYear,
                                            String mode,
                                            Long customerId,
                                            String collectedBy) {
        Company company = accessControlService.getCurrentCompany(email);
        DateRange dateRange = resolveRange(startDate, endDate, financialYear);
        List<Invoice> invoices = filterInvoices(invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company), dateRange, customerId);
        List<Payment> payments = filterPayments(paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company), dateRange, mode, customerId, collectedBy);
        List<Expense> expenses = filterExpenses(expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company), dateRange, customerId);
        Summary summary = summary(invoices, payments, expenses);
        return PaymentHierarchyResponse.builder()
                .nodeId("summary")
                .nodeType("summary")
                .companyName(company.getName())
                .totalReceivable(summary.totalReceivable())
                .totalCollected(summary.totalCollected())
                .totalOutstanding(summary.totalOutstanding())
                .totalExpense(summary.totalExpense())
                .netRevenue(summary.netRevenue())
                .nodes(List.of())
                .records(paymentRecords(payments))
                .build();
    }

    private List<PaymentHierarchyNodeResponse> rootNodes(Summary summary) {
        return List.of(
                node("receivable", "company", "metric", "Total Sale", "Click to view invoices", summary.totalReceivable(), summary.invoiceCount(), true, "sales"),
                node("collected", "company", "collected", "Total Collection", "Click to view payment modes", summary.totalCollected(), summary.paymentCount(), true, "success"),
                node("outstanding", "company", "metric", "Total Outstanding", "Click to view pending invoices", summary.totalOutstanding(), summary.outstandingInvoiceCount(), true, "danger"),
                node("expense", "company", "metric", "Total Expense", "Recorded expenses", summary.totalExpense(), summary.expenseCount(), false, "expense"),
                node("netRevenue", "company", "metric", "Net Revenue", "Total sale minus expense", summary.netRevenue(), summary.invoiceCount(), false, "net")
        );
    }

    private List<PaymentHierarchyNodeResponse> invoiceNodes(List<Invoice> invoices, String parentId, boolean balanceOnly) {
        return invoices.stream()
                .map(invoice -> node("invoice:" + parentId + ":" + invoice.getId(), parentId, "record", invoice.getInvoiceNo(), invoice.getCustomer().getName(), balanceOnly ? invoice.getBalanceAmount() : invoice.getTotalAmount(), 1L, false, parentId))
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> modeNodes(List<Payment> payments, Map<String, String> modeLabels) {
        Map<String, List<Payment>> byMode = payments.stream()
                .collect(Collectors.groupingBy(Payment::getMode));
        Set<String> modeCodes = new LinkedHashSet<>(modeLabels.keySet());
        modeCodes.addAll(byMode.keySet());
        return modeCodes.stream()
                .filter(Objects::nonNull)
                .map(mode -> {
                    List<Payment> rows = byMode.getOrDefault(mode, List.of());
                    return node("mode:" + mode, "collected", "mode", modeLabels.getOrDefault(mode, label(mode)), "Payment mode", sumPayments(rows), (long) rows.size(), !rows.isEmpty(), toneForMode(mode));
                })
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> yearNodes(List<Payment> payments, String mode) {
        return payments.stream()
                .filter(payment -> payment.getPaymentDate() != null)
                .collect(Collectors.groupingBy(payment -> payment.getPaymentDate().getYear()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<Integer, List<Payment>>comparingByKey().reversed())
                .map(entry -> node("year:" + mode + ":" + entry.getKey(), "mode:" + mode, "year", String.valueOf(entry.getKey()), "Year wise collection", sumPayments(entry.getValue()), (long) entry.getValue().size(), true, "year"))
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> monthNodes(List<Payment> payments, String mode, int year) {
        return payments.stream()
                .collect(Collectors.groupingBy(payment -> payment.getPaymentDate().getMonthValue()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String label = Month.of(entry.getKey()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    return node("month:" + mode + ":" + year + ":" + entry.getKey(), "year:" + mode + ":" + year, "month", label, "Month wise collection", sumPayments(entry.getValue()), (long) entry.getValue().size(), true, "month");
                })
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> dayNodes(List<Payment> payments, String mode, int year, int month) {
        return payments.stream()
                .collect(Collectors.groupingBy(Payment::getPaymentDate))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> node("day:" + mode + ":" + entry.getKey(), "month:" + mode + ":" + year + ":" + month, "day", dayLabel(entry.getKey()), "Day wise collection", sumPayments(entry.getValue()), (long) entry.getValue().size(), true, "day"))
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> recordNodes(List<Payment> payments, String mode, LocalDate day) {
        return payments.stream()
                .sorted(Comparator.comparing(Payment::getId).reversed())
                .map(payment -> node("payment:" + payment.getId(), "day:" + mode + ":" + day, "record", payment.getInvoice() == null ? "Unlinked Payment" : payment.getInvoice().getInvoiceNo(), payment.getCustomer().getName(), scale(payment.getAmount()), 1L, false, "record"))
                .toList();
    }

    private List<PaymentHierarchyRecordResponse> paymentRecords(List<Payment> payments) {
        return payments.stream()
                .sorted(Comparator.comparing(Payment::getPaymentDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(Payment::getId, Comparator.reverseOrder()))
                .map(payment -> PaymentHierarchyRecordResponse.builder()
                        .paymentId(payment.getId())
                        .invoiceNo(payment.getInvoice() == null ? "--" : payment.getInvoice().getInvoiceNo())
                        .customerName(payment.getCustomer().getName())
                        .amount(scale(payment.getAmount()))
                        .collectedBy(auditNameResolver.displayName(payment.getCreatedBy()))
                        .paymentMode(label(payment.getMode()))
                        .paymentDate(payment.getPaymentDate())
                        .build())
                .toList();
    }

    private List<Payment> filterByMode(List<Payment> payments, String mode) {
        String normalizedMode = normalizeMode(mode);
        if (normalizedMode == null) {
            return List.of();
        }
        return payments.stream().filter(payment -> normalizedMode.equalsIgnoreCase(payment.getMode())).toList();
    }

    private List<Invoice> filterInvoices(List<Invoice> invoices, DateRange range, Long customerId) {
        return invoices.stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), range))
                .filter(invoice -> customerId == null || invoice.getCustomer().getId().equals(customerId))
                .toList();
    }

    private List<Payment> filterPayments(List<Payment> payments, DateRange range, String mode, Long customerId, String collectedBy) {
        String normalizedMode = normalizeMode(mode);
        String normalizedCollector = collectedBy == null || collectedBy.isBlank() ? null : collectedBy.trim();
        return payments.stream()
                .filter(payment -> isWithinRange(payment.getPaymentDate(), range))
                .filter(payment -> normalizedMode == null || normalizedMode.equalsIgnoreCase(payment.getMode()))
                .filter(payment -> customerId == null || payment.getCustomer().getId().equals(customerId))
                .filter(payment -> normalizedCollector == null || matchesCollector(payment, normalizedCollector))
                .toList();
    }

    private List<Expense> filterExpenses(List<Expense> expenses, DateRange range, Long customerId) {
        return expenses.stream()
                .filter(expense -> isWithinRange(expense.getExpenseDate(), range))
                .filter(expense -> customerId == null
                        || (expense.getCustomer() != null && expense.getCustomer().getId().equals(customerId))
                        || (expense.getInvoice() != null && expense.getInvoice().getCustomer().getId().equals(customerId)))
                .toList();
    }

    private boolean matchesCollector(Payment payment, String normalizedCollector) {
        String rawCreatedBy = payment.getCreatedBy();
        String displayName = auditNameResolver.displayName(rawCreatedBy);
        return (rawCreatedBy != null && rawCreatedBy.equalsIgnoreCase(normalizedCollector))
                || (displayName != null && displayName.equalsIgnoreCase(normalizedCollector));
    }

    private boolean isWithinRange(LocalDate date, DateRange range) {
        if (date == null) {
            return false;
        }
        if (range.startDate() != null && date.isBefore(range.startDate())) {
            return false;
        }
        return range.endDate() == null || !date.isAfter(range.endDate());
    }

    private DateRange resolveRange(LocalDate startDate, LocalDate endDate, Integer financialYear) {
        if (startDate != null || endDate != null) {
            return new DateRange(startDate, endDate);
        }
        if (financialYear != null) {
            return new DateRange(LocalDate.of(financialYear, 4, 1), LocalDate.of(financialYear + 1, 3, 31));
        }
        return new DateRange(null, null);
    }

    private boolean matchesMonth(Payment payment, int year, int month) {
        LocalDate date = payment.getPaymentDate();
        return date != null && date.getYear() == year && date.getMonthValue() == month;
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        return mode.trim().toUpperCase(Locale.ENGLISH);
    }

    private Summary summary(List<Invoice> invoices, List<Payment> payments, List<Expense> expenses) {
        BigDecimal receivable = invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoices.stream().map(Invoice::getBalanceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long outstandingInvoiceCount = invoices.stream()
                .filter(invoice -> scale(invoice.getBalanceAmount()).compareTo(BigDecimal.ZERO) > 0)
                .count();
        return new Summary(scale(receivable), scale(collected), scale(outstanding), scale(expense), scale(receivable.subtract(expense)), (long) invoices.size(), (long) payments.size(), outstandingInvoiceCount, (long) expenses.size());
    }

    private BigDecimal sumPayments(List<Payment> payments) {
        return scale(payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private PaymentHierarchyNodeResponse node(String id, String parentId, String type, String label, String subtitle, BigDecimal amount, Long count, boolean hasChildren, String tone) {
        return PaymentHierarchyNodeResponse.builder()
                .id(id)
                .parentId(parentId)
                .type(type)
                .label(label)
                .subtitle(subtitle)
                .amount(scale(amount))
                .count(count)
                .hasChildren(hasChildren)
                .tone(tone)
                .build();
    }

    private String label(String mode) {
        if (mode == null || mode.isBlank()) {
            return "--";
        }
        return java.util.Arrays.stream(mode.trim().split("_"))
                .map(part -> part.charAt(0) + part.substring(1).toLowerCase(Locale.ENGLISH))
                .collect(Collectors.joining(" "));
    }

    private String dayLabel(LocalDate day) {
        return String.format("%02d-%s", day.getDayOfMonth(), day.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
    }

    private String toneForMode(String mode) {
        return switch (normalizeMode(mode) == null ? "" : normalizeMode(mode)) {
            case "CASH" -> "cash";
            case "UPI" -> "upi";
            case "CARD" -> "card";
            case "BANK_TRANSFER" -> "bank";
            case "CHEQUE" -> "cheque";
            default -> "other";
        };
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record Summary(BigDecimal totalReceivable, BigDecimal totalCollected, BigDecimal totalOutstanding, BigDecimal totalExpense, BigDecimal netRevenue, Long invoiceCount, Long paymentCount, Long outstandingInvoiceCount, Long expenseCount) {
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
