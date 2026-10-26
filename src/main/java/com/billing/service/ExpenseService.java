package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.expense.ExpenseRequest;
import com.billing.dto.expense.ExpenseResponse;
import com.billing.dto.expense.ProfitLossPointResponse;
import com.billing.dto.expense.ProfitLossReportResponse;
import com.billing.dto.expense.ProfitabilityResponse;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Expense;
import com.billing.entity.ExpenseCategory;
import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import com.billing.entity.User;
import com.billing.entity.enums.ExpenseType;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.ExpenseRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.InvoiceItemAllocationRepository;
import com.billing.repository.PaymentRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    /** Compact period bucket label for charts/tables (e.g. "Oct 2026"), matching GstReportService. */
    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final AccessControlService accessControlService;
    private final CustomerService customerService;
    private final InvoiceService invoiceService;
    private final ExpenseCategoryService expenseCategoryService;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;
    private final RevenueCalculationService revenueCalculationService;
    private final InvoiceItemAllocationRepository invoiceItemAllocationRepository;

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> page(String email,
                                               String search,
                                               ExpenseType expenseType,
                                               Long categoryId,
                                               Long customerId,
                                               Long invoiceId,
                                               LocalDate startDate,
                                               LocalDate endDate,
                                               RoleName createdByRole,
                                               Boolean deleted,
                                               int page,
                                               int size) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        // Ek page me createdBy/updatedBy wahi users repeat hote hain: naam ek hi baar DB se.
        UnaryOperator<String> auditNames = auditNameResolver.memoized();
        return PageResponse.from(expenseRepository.searchExpenses(
                company,
                blankToNull(search),
                expenseType,
                categoryId,
                customerId,
                invoiceId,
                startDate,
                endDate,
                createdByRole,
                deleted,
                pageRequest(page, size)
        ).map(expense -> toResponse(expense, auditNames)));
    }

    /**
     * Expenses page ke 4 KPI cards (Total / This Month / Today / Category). Pehle UI
     * 1000 rows mangwa ke JS me jodta tha — ab SQL aggregate, aur 1000+ par bhi sahi total.
     * today / monthStart client bhejta hai (browser ka din, pehle jaisa); na aaye to IST.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(Map<String, Object> param, String email) {
        accessControlService.getCurrentCompany(email);
        LocalDate today = DataTypeUtility.parseFlexibleDate(param, "today");
        if (today == null) {
            today = com.billing.core.AppDateTime.istToday();
        }
        LocalDate monthStart = DataTypeUtility.parseFlexibleDate(param, "monthStart");
        if (monthStart == null) {
            monthStart = today.withDayOfMonth(1);
        }
        Long categoryId = DataTypeUtility.getForeignKeyValue(param.get("categoryId"));
        BigDecimal totalAmount = expenseRepository.sumActiveAmount(null, null, null);
        BigDecimal categoryAmount = categoryId == null ? totalAmount : expenseRepository.sumActiveAmount(null, null, categoryId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", scale(totalAmount));
        summary.put("thisMonth", scale(expenseRepository.sumActiveAmount(monthStart, today, null)));
        summary.put("today", scale(expenseRepository.sumActiveAmount(today, today, null)));
        summary.put("category", scale(categoryAmount));
        summary.put("categoryCount", expenseRepository.countActiveByCategory(categoryId));
        return summary;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> page(Map<String, Object> param, String email) {
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        ExpenseType expenseType = null;
        String expenseTypeStr = DataTypeUtility.stringValue(param.get("expenseType"));
        if (expenseTypeStr.length() > 0) {
            try {
                expenseType = ExpenseType.valueOf(expenseTypeStr);
            } catch (Exception ignore) {
            }
        }
        Long categoryId = DataTypeUtility.getForeignKeyValue(param.get("categoryId"));
        Long customerId = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        Long invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoiceId"));
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        RoleName createdByRole = null;
        String roleStr = DataTypeUtility.stringValue(param.get("createdByRole"));
        if (roleStr.length() > 0) {
            try {
                createdByRole = RoleName.valueOf(roleStr);
            } catch (Exception ignore) {
            }
        }
        // Invoices jaisa: ACTIVE (default) / DELETED / ALL.
        String recordStatus = DataTypeUtility.stringValue(param.get("recordStatus")).toUpperCase(java.util.Locale.ROOT);
        Boolean deleted = "ALL".equals(recordStatus) ? null : "DELETED".equals(recordStatus) ? Boolean.TRUE : Boolean.FALSE;
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return page(email, search, expenseType, categoryId, customerId, invoiceId, startDate, endDate, createdByRole, deleted, page, size);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(String email, Long expenseId) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        return toResponse(getExpenseOrThrow(company, expenseId));
    }

    @Transactional
    public ExpenseResponse create(Map<String, Object> param, String email) {
        ExpenseRequest expenseRequest = mapToRequest(param);
        return create(email, expenseRequest);
    }

    @Transactional
    public ExpenseResponse create(String email, ExpenseRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Expense expense = new Expense();
        expense.setCompany(company);
        applyRequest(company, expense, request);
        Expense saved = expenseRepository.save(expense);
        auditLogService.logCreate(email, company, "Expense", "Expense", saved.getId(), snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public ExpenseResponse update(Map<String, Object> param, Long expenseId, String email) {
        ExpenseRequest expenseRequest = mapToRequest(param);
        return update(email, expenseId, expenseRequest);
    }

    @Transactional
    public ExpenseResponse update(String email, Long expenseId, ExpenseRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Expense expense = getExpenseOrThrow(company, expenseId);
        if (expense.isDeleted()) {
            throw new BadRequestException("This expense is deleted and cannot be edited");
        }
        Map<String, Object> oldData = snapshot(expense);
        applyRequest(company, expense, request);
        Expense saved = expenseRepository.save(expense);
        auditLogService.logUpdate(email, company, "Expense", "Expense", saved.getId(), oldData, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(String email, Long expenseId) {
        Company company = accessControlService.getCurrentCompany(email);
        Expense expense = getExpenseOrThrow(company, expenseId);
        if (expense.isDeleted()) {
            throw new BadRequestException("Expense is already deleted");
        }
        Map<String, Object> oldData = snapshot(expense);
        // Soft delete (baaki masters jaisa): row DB me rehti hai, list/reports se bahar.
        expense.setDeleted(true);
        expenseRepository.save(expense);
        auditLogService.logDelete(email, company, "Expense", "Expense", expenseId, oldData);
    }

    @Transactional(readOnly = true)
    public ProfitabilityResponse customerProfitability(Map<String, Object> param, String email, Long customerId) {
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        return customerProfitability(email, customerId, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public ProfitabilityResponse customerProfitability(String email, Long customerId, LocalDate startDate, LocalDate endDate) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerService.getCustomerOrThrow(company, customerId);
        BigDecimal revenue = invoiceRepository.findByCustomerOrderByInvoiceDateDescIdDesc( customer).stream()
                .filter(invoice -> inRange(invoice.getInvoiceDate(), startDate, endDate))
                .map(ExpenseService::revenueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costOfGoodsSold = invoiceItemAllocationRepository.sumActiveCostByCustomer(company, customerId, startDate, endDate);
        // SQL sum (pehle range ke SAARE expenses graph samet memory me aate the, phir Java filter).
        BigDecimal expense = expenseRepository.sumActiveAmountByCustomer(customerId, startDate, endDate);
        return profitability(customerId, customer.getName(), revenue, costOfGoodsSold, expense);
    }

    @Transactional(readOnly = true)
    public ProfitabilityResponse invoiceProfitability(String email, Long invoiceId) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = invoiceService.getInvoiceOrThrow(company, invoiceId);
        BigDecimal revenue = scale(revenueOf(invoice));
        BigDecimal costOfGoodsSold = scale(invoiceItemAllocationRepository.sumActiveCostByInvoice(company, invoiceId));
        BigDecimal expense = expenseRepository.searchExpenses(company, null, null, null, null, invoiceId, null, null, null, Boolean.FALSE, Pageable.unpaged()).getContent().stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return profitability(invoiceId, invoice.getInvoiceNo(), revenue, costOfGoodsSold, expense);
    }

    @Transactional(readOnly = true)
    public ProfitLossReportResponse profitLossReport(Map<String, Object> param, String email) {
        ExpenseType expenseType = null;
        String expenseTypeStr = DataTypeUtility.stringValue(param.get("expenseType"));
        if (expenseTypeStr.length() > 0) {
            try {
                expenseType = ExpenseType.valueOf(expenseTypeStr);
            } catch (Exception ignore) {
            }
        }
        Long categoryId = DataTypeUtility.getForeignKeyValue(param.get("categoryId"));
        Long customerId = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        Long invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoiceId"));
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        RoleName createdByRole = null;
        String roleStr = DataTypeUtility.stringValue(param.get("createdByRole"));
        if (roleStr.length() > 0) {
            try {
                createdByRole = RoleName.valueOf(roleStr);
            } catch (Exception ignore) {
            }
        }
        return profitLossReport(email, expenseType, categoryId, customerId, invoiceId, startDate, endDate, createdByRole);
    }

    @Transactional(readOnly = true)
    public ProfitLossReportResponse profitLossReport(String email,
                                                     ExpenseType expenseType,
                                                     Long categoryId,
                                                     Long customerId,
                                                     Long invoiceId,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     RoleName createdByRole) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        // Pehle PageRequest.of(0, 1000): 1000 se zyada expenses par totals chup-chaap galat.
        List<Expense> expenses = expenseRepository.searchExpenses(company, null, expenseType, categoryId, customerId, invoiceId, startDate, endDate, createdByRole, Boolean.FALSE, Pageable.unpaged()).getContent();
        // Date range DB me (pehle poori invoices/payments table memory me aati thi).
        List<Invoice> invoices = invoiceRepository.findActiveInRange(startDate, endDate).stream()
                .filter(invoice -> customerId == null || invoice.getCustomer().getId().equals(customerId))
                .filter(invoice -> invoiceId == null || invoice.getId().equals(invoiceId))
                .toList();
        List<Payment> payments = paymentRepository.findActiveInRange(startDate, endDate).stream()
                .filter(payment -> customerId == null || payment.getCustomer().getId().equals(customerId))
                .filter(payment -> invoiceId == null || (payment.getInvoice() != null && payment.getInvoice().getId().equals(invoiceId)))
                .toList();
        BigDecimal revenue = invoices.stream().map(ExpenseService::revenueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        // COGS par bhi wahi customer/invoice filter jo revenue par hai.
        BigDecimal costOfGoodsSold = scale(invoiceItemAllocationRepository.sumActiveCostFiltered(customerId, invoiceId, startDate, endDate));
        BigDecimal expense = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> categoryTotals = expenses.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory().getCategoryName(), LinkedHashMap::new, Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));

        List<ProfitLossPointResponse> expenseByCategory = categoryTotals.entrySet().stream()
                .map(entry -> ProfitLossPointResponse.builder().label(entry.getKey()).expense(scale(entry.getValue())).value(scale(entry.getValue())).build())
                .toList();

        Map<YearMonth, BigDecimal> revenueByMonth = invoices.stream()
                .collect(Collectors.groupingBy(invoice -> YearMonth.from(invoice.getInvoiceDate()), Collectors.reducing(BigDecimal.ZERO, ExpenseService::revenueOf, BigDecimal::add)));
        Map<YearMonth, BigDecimal> collectionByMonth = payments.stream()
                .collect(Collectors.groupingBy(payment -> YearMonth.from(payment.getPaymentDate()), Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));
        Map<YearMonth, BigDecimal> expenseByMonth = expenses.stream()
                .collect(Collectors.groupingBy(item -> YearMonth.from(item.getExpenseDate()), Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));

        // Month-wise COGS ek GROUP BY query me; query khud startDate/endDate tak clip karti hai,
        // isliye har mahine ki alag (monthStart..monthEnd) query jaisa hi result.
        Map<YearMonth, BigDecimal> cogsByMonth = new LinkedHashMap<>();
        for (Object[] monthCogsRow : invoiceItemAllocationRepository.sumActiveCostFilteredByMonth(customerId, invoiceId, startDate, endDate)) {
            int cogsYear = DataTypeUtility.integerValue(monthCogsRow[0]);
            int cogsMonth = DataTypeUtility.integerValue(monthCogsRow[1]);
            if (cogsYear <= 0 || cogsMonth < 1 || cogsMonth > 12) {
                continue;
            }
            BigDecimal monthCost = monthCogsRow[2] instanceof BigDecimal monthCostValue ? monthCostValue : new BigDecimal(String.valueOf(monthCogsRow[2]));
            cogsByMonth.put(YearMonth.of(cogsYear, cogsMonth), monthCost);
        }

        List<ProfitLossPointResponse> revenueVsExpense = java.util.stream.Stream.concat(revenueByMonth.keySet().stream(), java.util.stream.Stream.concat(collectionByMonth.keySet().stream(), expenseByMonth.keySet().stream()))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(month -> {
                    BigDecimal monthRevenue = revenueByMonth.getOrDefault(month, BigDecimal.ZERO);
                    BigDecimal monthCollection = collectionByMonth.getOrDefault(month, BigDecimal.ZERO);
                    BigDecimal monthExpense = expenseByMonth.getOrDefault(month, BigDecimal.ZERO);
                    // Chart ka "Net profit" cards jaisa hi ho: revenue − COGS − expense. Pehle yahan
                    // collections − expense (cash) tha — card ₹70K, chart ₹2.83L dikhata tha.
                    BigDecimal monthCogs = scale(cogsByMonth.getOrDefault(month, BigDecimal.ZERO));
                    return ProfitLossPointResponse.builder()
                            .label(month.format(MONTH_LABEL_FORMAT))
                            .revenue(scale(monthRevenue))
                            .expense(scale(monthExpense))
                            .netRevenue(revenueCalculationService.netRevenue(monthCollection, monthExpense))
                            .costOfGoodsSold(monthCogs)
                            .netProfit(revenueCalculationService.netProfit(monthRevenue, monthCogs, monthExpense))
                            .build();
                })
                .toList();

        return ProfitLossReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .revenue(scale(revenue))
                .costOfGoodsSold(scale(costOfGoodsSold))
                .expense(scale(expense))
                .grossProfit(revenueCalculationService.grossProfit(revenue, costOfGoodsSold))
                .netProfit(revenueCalculationService.netProfit(revenue, costOfGoodsSold, expense))
                .expenseByCategory(expenseByCategory)
                .revenueVsExpense(revenueVsExpense)
                .build();
    }

    /**
     * Revenue = taxable value (discount ke baad, GST se pehle). GST sarkar ka
     * paisa hai; pehle totalAmount (GST samet) lene se gross profit utna hi zyada dikhta tha.
     */
    private static BigDecimal revenueOf(Invoice invoice) {
        return invoice.getTaxableAmount() != null ? invoice.getTaxableAmount() : BigDecimal.ZERO;
    }

    private ExpenseRequest mapToRequest(Map<String, Object> param) {
        ExpenseRequest expenseRequest = new ExpenseRequest();
        String expenseTypeStr = DataTypeUtility.stringValue(param.get("expenseType"));
        if (expenseTypeStr.length() > 0) {
            try {
                expenseRequest.setExpenseType(ExpenseType.valueOf(expenseTypeStr));
            } catch (Exception ignore) {
                expenseRequest.setExpenseType(null);
            }
        }
        expenseRequest.setCategoryId(DataTypeUtility.getForeignKeyValue(param.get("categoryId")));
        expenseRequest.setCustomerId(DataTypeUtility.getForeignKeyValue(param.get("customerId")));
        expenseRequest.setInvoiceId(DataTypeUtility.getForeignKeyValue(param.get("invoiceId")));
        expenseRequest.setAmount(DataTypeUtility.bigDecimalObjectValue(param.get("amount")));
        if (expenseRequest.getAmount() == null) {
            Double amountValue = DataTypeUtility.doubleObjectValue(param.get("amount"));
            if (amountValue != null) {
                expenseRequest.setAmount(BigDecimal.valueOf(amountValue));
            }
        }
        String expenseDateStr = DataTypeUtility.stringValue(param.get("expenseDate"));
        LocalDate parsedExpenseDate = DataTypeUtility.parseLocalDate(expenseDateStr, "yyyy-MM-dd");
        if (parsedExpenseDate == null) {
            parsedExpenseDate = DataTypeUtility.parseLocalDate(expenseDateStr, "dd-MM-yyyy");
        }
        if (parsedExpenseDate == null) {
            parsedExpenseDate = DataTypeUtility.parseLocalDate(expenseDateStr, "yyyy/MM/dd");
        }
        expenseRequest.setExpenseDate(parsedExpenseDate);
        String description = DataTypeUtility.stringValue(param.get("description"));
        if (description != null && description.isEmpty()) {
            description = null;
        }
        expenseRequest.setDescription(description);
        String attachmentUrl = DataTypeUtility.stringValue(param.get("attachmentUrl"));
        if (attachmentUrl != null && attachmentUrl.isEmpty()) {
            attachmentUrl = null;
        }
        expenseRequest.setAttachmentUrl(attachmentUrl);
        return expenseRequest;
    }

    private void applyRequest(Company company, Expense expense, ExpenseRequest request) {
        // Map endpoints par @Valid nahi chalta: zero/negative amount aur khali date save ho jaate the.
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Expense amount must be greater than 0");
        }
        if (request.getExpenseDate() == null) {
            throw new BadRequestException("Expense date is required");
        }
        ExpenseCategory category = expenseCategoryService.getActiveByIdOrThrow(company, request.getCategoryId());
        Customer customer = null;
        Invoice invoice = null;
        if (request.getExpenseType() == ExpenseType.CUSTOMER_RELATED) {
            if (request.getCustomerId() == null) {
                throw new BadRequestException("Customer is required for customer related expense");
            }
            customer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        }
        if (request.getExpenseType() == ExpenseType.INVOICE_RELATED) {
            if (request.getInvoiceId() == null) {
                throw new BadRequestException("Invoice is required for invoice related expense");
            }
            invoice = invoiceService.getInvoiceOrThrow(company, request.getInvoiceId());
            customer = invoice.getCustomer();
            if (request.getCustomerId() != null && !request.getCustomerId().equals(customer.getId())) {
                throw new BadRequestException("Expense customer and invoice customer must match");
            }
        }
        expense.setExpenseType(request.getExpenseType());
        expense.setCategory(category);
        expense.setCustomer(customer);
        expense.setInvoice(invoice);
        expense.setAmount(scale(request.getAmount()));
        expense.setExpenseDate(request.getExpenseDate());
        expense.setDescription(blankToNull(request.getDescription()));
        expense.setAttachmentUrl(blankToNull(request.getAttachmentUrl()));
    }

    private Expense getExpenseOrThrow(Company company, Long expenseId) {
        return expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return toResponse(expense, auditNameResolver::displayName);
    }

    private ExpenseResponse toResponse(Expense expense, UnaryOperator<String> auditNames) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .expenseType(expense.getExpenseType().name())
                .categoryId(expense.getCategory().getId())
                .categoryName(expense.getCategory().getCategoryName())
                .customerId(expense.getCustomer() != null ? expense.getCustomer().getId() : null)
                .customerName(expense.getCustomer() != null ? expense.getCustomer().getName() : null)
                .invoiceId(expense.getInvoice() != null ? expense.getInvoice().getId() : null)
                .invoiceNo(expense.getInvoice() != null ? expense.getInvoice().getInvoiceNo() : null)
                .amount(scale(expense.getAmount()))
                .expenseDate(expense.getExpenseDate())
                .description(expense.getDescription())
                .attachmentUrl(expense.getAttachmentUrl())
                .deleted(expense.isDeleted())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .createdBy(auditNames.apply(expense.getCreatedBy()))
                .updatedBy(auditNames.apply(expense.getUpdatedBy()))
                .build();
    }

    private ProfitabilityResponse profitability(Long id, String name, BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal expense) {
        return ProfitabilityResponse.builder()
                .referenceId(id)
                .referenceName(name)
                .revenue(scale(revenue))
                .costOfGoodsSold(scale(costOfGoodsSold))
                .expense(scale(expense))
                .grossProfit(revenueCalculationService.grossProfit(revenue, costOfGoodsSold))
                .netRevenue(revenueCalculationService.netProfit(revenue, costOfGoodsSold, expense))
                .build();
    }

    private Map<String, Object> snapshot(Expense expense) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expenseType", expense.getExpenseType() != null ? expense.getExpenseType().name() : null);
        data.put("categoryId", expense.getCategory() != null ? expense.getCategory().getId() : null);
        data.put("customerId", expense.getCustomer() != null ? expense.getCustomer().getId() : null);
        data.put("invoiceId", expense.getInvoice() != null ? expense.getInvoice().getId() : null);
        data.put("amount", scale(expense.getAmount()));
        data.put("expenseDate", expense.getExpenseDate());
        data.put("description", expense.getDescription());
        data.put("attachmentUrl", expense.getAttachmentUrl());
        return data;
    }

    private boolean inRange(LocalDate value, LocalDate startDate, LocalDate endDate) {
        return value != null
                && (startDate == null || !value.isBefore(startDate))
                && (endDate == null || !value.isAfter(endDate));
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 1000)), Sort.by(Sort.Direction.DESC, "expenseDate").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
