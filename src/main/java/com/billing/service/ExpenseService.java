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
import com.billing.entity.User;
import com.billing.entity.enums.ExpenseType;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.ExpenseRepository;
import com.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final AccessControlService accessControlService;
    private final CustomerService customerService;
    private final InvoiceService invoiceService;
    private final ExpenseCategoryService expenseCategoryService;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;

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
                                              int page,
                                              int size) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.isSuperAdmin(user) ? null : accessControlService.requireCompany(user);
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
                pageRequest(page, size)
        ).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(String email, Long expenseId) {
        User user = accessControlService.getCurrentUser(email);
        if (accessControlService.isSuperAdmin(user)) {
            return expenseRepository.findById(expenseId)
                    .map(this::toResponse)
                    .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        }
        Company company = accessControlService.requireCompany(user);
        return toResponse(getExpenseOrThrow(company, expenseId));
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
    public ExpenseResponse update(String email, Long expenseId, ExpenseRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Expense expense = getExpenseOrThrow(company, expenseId);
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
        Map<String, Object> oldData = snapshot(expense);
        expenseRepository.delete(expense);
        auditLogService.logDelete(email, company, "Expense", "Expense", expenseId, oldData);
    }

    @Transactional(readOnly = true)
    public ProfitabilityResponse customerProfitability(String email, Long customerId, LocalDate startDate, LocalDate endDate) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerService.getCustomerOrThrow(company, customerId);
        BigDecimal revenue = invoiceRepository.findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(company, customer).stream()
                .filter(invoice -> inRange(invoice.getInvoiceDate(), startDate, endDate))
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company).stream()
                .filter(item -> item.getCustomer() != null && item.getCustomer().getId().equals(customerId))
                .filter(item -> inRange(item.getExpenseDate(), startDate, endDate))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return profitability(customerId, customer.getName(), revenue, expense);
    }

    @Transactional(readOnly = true)
    public ProfitabilityResponse invoiceProfitability(String email, Long invoiceId) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = invoiceService.getInvoiceOrThrow(company, invoiceId);
        BigDecimal expense = expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company).stream()
                .filter(item -> item.getInvoice() != null && item.getInvoice().getId().equals(invoiceId))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return profitability(invoiceId, invoice.getInvoiceNo(), invoice.getTotalAmount(), expense);
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
        Company company = accessControlService.isSuperAdmin(user) ? null : accessControlService.requireCompany(user);
        List<Expense> expenses = expenseRepository.searchExpenses(company, null, expenseType, categoryId, customerId, invoiceId, startDate, endDate, createdByRole, PageRequest.of(0, 1000)).getContent();
        List<Invoice> invoices = (company == null ? invoiceRepository.findAllByOrderByInvoiceDateDescIdDesc() : invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company)).stream()
                .filter(invoice -> customerId == null || invoice.getCustomer().getId().equals(customerId))
                .filter(invoice -> invoiceId == null || invoice.getId().equals(invoiceId))
                .filter(invoice -> inRange(invoice.getInvoiceDate(), startDate, endDate))
                .toList();
        BigDecimal revenue = invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> categoryTotals = expenses.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory().getCategoryName(), LinkedHashMap::new, Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));

        List<ProfitLossPointResponse> expenseByCategory = categoryTotals.entrySet().stream()
                .map(entry -> ProfitLossPointResponse.builder().label(entry.getKey()).expense(scale(entry.getValue())).value(scale(entry.getValue())).build())
                .toList();

        Map<YearMonth, BigDecimal> revenueByMonth = invoices.stream()
                .collect(Collectors.groupingBy(invoice -> YearMonth.from(invoice.getInvoiceDate()), Collectors.reducing(BigDecimal.ZERO, Invoice::getTotalAmount, BigDecimal::add)));
        Map<YearMonth, BigDecimal> expenseByMonth = expenses.stream()
                .collect(Collectors.groupingBy(item -> YearMonth.from(item.getExpenseDate()), Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));

        List<ProfitLossPointResponse> revenueVsExpense = java.util.stream.Stream.concat(revenueByMonth.keySet().stream(), expenseByMonth.keySet().stream())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(month -> {
                    BigDecimal monthRevenue = revenueByMonth.getOrDefault(month, BigDecimal.ZERO);
                    BigDecimal monthExpense = expenseByMonth.getOrDefault(month, BigDecimal.ZERO);
                    return ProfitLossPointResponse.builder()
                            .label(month.toString())
                            .revenue(scale(monthRevenue))
                            .expense(scale(monthExpense))
                            .netRevenue(scale(monthRevenue.subtract(monthExpense)))
                            .build();
                })
                .toList();

        return ProfitLossReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .revenue(scale(revenue))
                .expense(scale(expense))
                .netProfit(scale(revenue.subtract(expense)))
                .expenseByCategory(expenseByCategory)
                .revenueVsExpense(revenueVsExpense)
                .build();
    }

    private void applyRequest(Company company, Expense expense, ExpenseRequest request) {
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
        return expenseRepository.findByIdAndCompany(expenseId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
    }

    private ExpenseResponse toResponse(Expense expense) {
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
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .createdBy(auditNameResolver.displayName(expense.getCreatedBy()))
                .updatedBy(auditNameResolver.displayName(expense.getUpdatedBy()))
                .build();
    }

    private ProfitabilityResponse profitability(Long id, String name, BigDecimal revenue, BigDecimal expense) {
        return ProfitabilityResponse.builder()
                .referenceId(id)
                .referenceName(name)
                .revenue(scale(revenue))
                .expense(scale(expense))
                .netRevenue(scale(revenue.subtract(expense)))
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
