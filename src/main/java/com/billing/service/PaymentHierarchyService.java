package com.billing.service;

import com.billing.dto.payment.PaymentHierarchyNodeResponse;
import com.billing.dto.payment.PaymentHierarchyRecordResponse;
import com.billing.dto.payment.PaymentHierarchyResponse;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Expense;
import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import com.billing.entity.enums.ExpenseType;
import com.billing.repository.ExpenseRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentHierarchyService {
    private static final DateTimeFormatter INDIAN_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AccessControlService accessControlService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final AuditNameResolver auditNameResolver;
    private final RevenueCalculationService revenueCalculationService;

    @Transactional(readOnly = true)
    public PaymentHierarchyResponse children(String email,
                                             String nodeType,
                                             String nodeId,
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
        DateRange range = resolveRange(startDate, endDate, financialYear);
        List<Invoice> invoices = filterInvoices(invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company), range, customerId);
        List<Payment> payments = filterPayments(paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company), range, mode, customerId, collectedBy);
        List<Expense> expenses = filterExpenses(expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company), range, customerId);
        Summary summary = summary(invoices, payments, expenses);
        String resolvedType = nodeType == null || nodeType.isBlank() ? "company" : nodeType.trim();

        List<PaymentHierarchyNodeResponse> nodes = switch (resolvedType) {
            case "company" -> topLevelNodes(invoices, payments, range);
            case "metric" -> metricTopLevelNodes(nodeId, invoices, payments, expenses, range);
            case "year" -> {
                String metric = metricFromNode(nodeId);
                if ("total-expense".equals(metric)) {
                    yield expenseMonthNodes(expensesForScope(expenses, nodeId), yearFromNode(nodeId), nodeId);
                }
                if ("net-revenue".equals(metric)) {
                    yield netMonthNodes(paymentsForScope(payments, nodeId), expensesForScope(expenses, nodeId), yearFromNode(nodeId), nodeId);
                }
                List<Payment> scopedPayments = paymentsForScope(payments, nodeId);
                yield metricMonthNodes(metric, includePaymentInvoices(invoicesForScope(invoices, nodeId), scopedPayments), scopedPayments, yearFromNode(nodeId), nodeId);
            }
            case "month", "period" -> {
                String metric = metricFromNode(nodeId);
                if ("total-expense".equals(metric)) {
                    yield expenseTypeNodes(expensesForScope(expenses, nodeId), nodeId);
                }
                if ("net-revenue".equals(metric)) {
                    yield List.of();
                }
                List<Payment> scopedPayments = paymentsForScope(payments, nodeId);
                yield metricCustomerNodes(metric, includePaymentInvoices(invoicesForScope(invoices, nodeId), scopedPayments), scopedPayments, nodeId);
            }
            case "customer" -> {
                String metric = metricFromNode(nodeId);
                List<Payment> scopedPayments = paymentsForCustomerScope(payments, nodeId);
                yield metricInvoiceNodes(metric, includePaymentInvoices(invoicesForCustomerScope(invoices, nodeId), scopedPayments), scopedPayments, nodeId);
            }
            case "expense_type" -> expenseCategoryNodes(expensesForExpenseTypeScope(expenses, nodeId), nodeId);
            case "expense_category" -> {
                List<Expense> categoryExpenses = expensesForCategoryScope(expenses, nodeId);
                ExpenseType expenseType = expenseTypeFromNode(nodeId);
                if (expenseType == ExpenseType.INVOICE_RELATED) {
                    yield expenseInvoiceNodes(categoryExpenses, nodeId);
                }
                if (expenseType == ExpenseType.CUSTOMER_RELATED) {
                    yield expenseCustomerNodes(categoryExpenses, nodeId, false);
                }
                yield expenseRecordNodes(categoryExpenses, nodeId);
            }
            case "expense_invoice" -> expenseCustomerNodes(expensesForExpenseInvoiceScope(expenses, nodeId), nodeId, false);
            case "expense_customer" -> expenseRecordNodes(expensesForExpenseCustomerScope(expenses, nodeId), nodeId);
            default -> List.of();
        };

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
                .records("invoice".equals(resolvedType) ? paymentRecords(paymentsForInvoice(payments, invoiceIdFromNode(nodeId))) : List.of())
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
        DateRange range = resolveRange(startDate, endDate, financialYear);
        List<Invoice> invoices = filterInvoices(invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company), range, customerId);
        List<Payment> payments = filterPayments(paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company), range, mode, customerId, collectedBy);
        List<Expense> expenses = filterExpenses(expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company), range, customerId);
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

    private List<PaymentHierarchyNodeResponse> topLevelNodes(List<Invoice> invoices, List<Payment> payments, DateRange range) {
        HierarchyDepth depth = hierarchyDepth(range);
        if (depth == HierarchyDepth.PERIOD) {
            return List.of(periodNode("period|" + iso(range.startDate()) + "|" + iso(range.endDate()), periodLabel(range), includePaymentInvoices(invoices, payments), payments, "company", "day"));
        }
        if (depth == HierarchyDepth.MONTH) {
            YearMonth month = range.startDate() != null ? YearMonth.from(range.startDate()) : YearMonth.now();
            return List.of(monthNode(month, includePaymentInvoices(invoices, payments), payments, "company"));
        }
        return java.util.stream.Stream.concat(
                        invoices.stream()
                                .map(Invoice::getInvoiceDate)
                                .filter(Objects::nonNull)
                                .map(LocalDate::getYear),
                        payments.stream()
                                .map(Payment::getPaymentDate)
                                .filter(Objects::nonNull)
                                .map(LocalDate::getYear)
                )
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(bucketYear -> {
                    List<Payment> yearPayments = paymentsForYear(payments, bucketYear);
                    List<Invoice> yearInvoices = includePaymentInvoices(invoices.stream()
                            .filter(invoice -> invoice.getInvoiceDate() != null && invoice.getInvoiceDate().getYear() == bucketYear)
                            .toList(), yearPayments);
                    return yearNode(bucketYear, yearInvoices, yearPayments, "company");
                })
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> metricTopLevelNodes(String metric, List<Invoice> invoices, List<Payment> payments, List<Expense> expenses, DateRange range) {
        String resolvedMetric = metric == null || metric.isBlank() ? "total-sales" : metric;
        if ("total-expense".equals(resolvedMetric)) {
            return expenseTopLevelNodes(expenses, range, resolvedMetric);
        }
        if ("net-revenue".equals(resolvedMetric)) {
            return netTopLevelNodes(payments, expenses, range, resolvedMetric);
        }
        List<Payment> scopedPayments = metricPayments(resolvedMetric, payments);
        List<Invoice> scopedInvoices = metricInvoices(resolvedMetric, invoices, scopedPayments);
        return metricTopLevelNodesForInvoices(resolvedMetric, scopedInvoices, scopedPayments, range);
    }

    private List<PaymentHierarchyNodeResponse> metricTopLevelNodesForInvoices(String metric, List<Invoice> invoices, List<Payment> payments, DateRange range) {
        HierarchyDepth depth = hierarchyDepth(range);
        if (depth == HierarchyDepth.PERIOD) {
            return List.of(metricPeriodNode(metric, "period|" + metric + "|" + iso(range.startDate()) + "|" + iso(range.endDate()), periodLabel(range), invoices, payments, metric, "day"));
        }
        if (depth == HierarchyDepth.MONTH) {
            YearMonth month = range.startDate() != null ? YearMonth.from(range.startDate()) : YearMonth.now();
            return List.of(metricMonthNode(metric, month, invoices, payments, metric));
        }
        return java.util.stream.Stream.concat(
                        invoices.stream()
                                .map(Invoice::getInvoiceDate)
                                .filter(Objects::nonNull)
                                .map(LocalDate::getYear),
                        payments.stream()
                                .map(Payment::getPaymentDate)
                                .filter(Objects::nonNull)
                                .map(LocalDate::getYear)
                )
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(bucketYear -> {
                    List<Payment> yearPayments = paymentsForYear(payments, bucketYear);
                    List<Invoice> yearInvoices = metricInvoices(metric, invoices.stream()
                            .filter(invoice -> invoice.getInvoiceDate() != null && invoice.getInvoiceDate().getYear() == bucketYear)
                            .toList(), yearPayments);
                    return metricYearNode(metric, bucketYear, yearInvoices, yearPayments, metric);
                })
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> metricMonthNodes(String metric, List<Invoice> invoices, List<Payment> payments, int year, String parentId) {
        return java.util.stream.Stream.concat(
                        invoices.stream()
                                .map(Invoice::getInvoiceDate)
                                .filter(Objects::nonNull)
                                .map(YearMonth::from),
                        payments.stream()
                                .map(Payment::getPaymentDate)
                                .filter(Objects::nonNull)
                                .map(YearMonth::from)
                )
                .filter(bucketMonth -> bucketMonth.getYear() == year)
                .distinct()
                .sorted()
                .map(bucketMonth -> {
                    List<Payment> monthPayments = paymentsForMonth(payments, bucketMonth);
                    List<Invoice> monthInvoices = metricInvoices(metric, invoices.stream()
                            .filter(invoice -> invoice.getInvoiceDate() != null && YearMonth.from(invoice.getInvoiceDate()).equals(bucketMonth))
                            .toList(), monthPayments);
                    return metricMonthNode(metric, bucketMonth, monthInvoices, monthPayments, parentId);
                })
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> metricCustomerNodes(String metric, List<Invoice> invoices, List<Payment> payments, String parentId) {
        return invoices.stream()
                .collect(Collectors.groupingBy(Invoice::getCustomer, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .map(entry -> {
                    Customer customer = entry.getKey();
                    List<Invoice> customerInvoices = metricInvoices(metric, entry.getValue(), payments.stream()
                            .filter(payment -> payment.getCustomer().getId().equals(customer.getId()))
                            .toList());
                    List<Payment> customerPayments = payments.stream()
                            .filter(payment -> payment.getCustomer().getId().equals(customer.getId()))
                            .toList();
                    BigDecimal amount = sumInvoiceMetric(customerInvoices, customerPayments, metric);
                    BigDecimal total = sumInvoices(customerInvoices);
                    BigDecimal collected = sumPayments(customerPayments);
                    BigDecimal outstanding = sumOutstanding(customerInvoices, customerPayments);
                    return node("customer|" + parentId + "|" + customer.getId(), parentId, "customer", customer.getName(), customer.getMobile(), amount,
                            total, collected, outstanding, (long) customerInvoices.size(), customerInvoices.size(), 1L, customerPayments.size(), true, "customer");
                })
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> metricInvoiceNodes(String metric, List<Invoice> invoices, List<Payment> payments, String parentId) {
        return invoices.stream()
                .sorted(Comparator.comparing(Invoice::getInvoiceDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(invoice -> {
                    List<Payment> invoicePayments = paymentsForInvoice(payments, invoice.getId());
                    BigDecimal total = scale(invoice.getTotalAmount());
                    BigDecimal collected = sumPayments(invoicePayments);
                    BigDecimal outstanding = invoiceOutstanding(invoice, invoicePayments);
                    BigDecimal amount = invoiceMetricAmount(invoice, invoicePayments, metric);
                    return node("invoice|" + metric + "|" + invoice.getId(), parentId, "invoice", invoice.getInvoiceNo(), invoice.getCustomer().getName(), amount,
                            total, collected, outstanding, 0L, 1L, 1L, invoicePayments.size(), false, "record");
                })
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> expenseTopLevelNodes(List<Expense> expenses, DateRange range, String metric) {
        HierarchyDepth depth = hierarchyDepth(range);
        if (depth == HierarchyDepth.PERIOD) {
            return List.of(expensePeriodNode(metric, "period|" + metric + "|" + iso(range.startDate()) + "|" + iso(range.endDate()), periodLabel(range), expenses, metric, "day"));
        }
        if (depth == HierarchyDepth.MONTH) {
            YearMonth month = range.startDate() != null ? YearMonth.from(range.startDate()) : YearMonth.now();
            return List.of(expenseMonthNode(metric, month, expenses, metric));
        }
        return expenses.stream()
                .filter(expense -> expense.getExpenseDate() != null)
                .collect(Collectors.groupingBy(expense -> expense.getExpenseDate().getYear(), LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<Integer, List<Expense>>comparingByKey().reversed())
                .map(entry -> expenseYearNode(metric, entry.getKey(), entry.getValue(), metric))
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> expenseMonthNodes(List<Expense> expenses, int year, String parentId) {
        return expenses.stream()
                .filter(expense -> expense.getExpenseDate() != null && expense.getExpenseDate().getYear() == year)
                .collect(Collectors.groupingBy(expense -> YearMonth.from(expense.getExpenseDate()), LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> expenseMonthNode("total-expense", entry.getKey(), entry.getValue(), parentId))
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> expenseTypeNodes(List<Expense> expenses, String parentId) {
        return expenses.stream()
                .collect(Collectors.groupingBy(Expense::getExpenseType, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> expenseTypeLabel(entry.getKey())))
                .map(entry -> {
                    BigDecimal amount = sumExpenses(entry.getValue());
                    return node("expense_type|" + parentId + "|" + entry.getKey().name(), parentId, "expense_type", expenseTypeLabel(entry.getKey()), dateRangeLabel(entry.getValue()), amount,
                            amount, BigDecimal.ZERO, BigDecimal.ZERO, expenseCategoryCount(entry.getValue()), 0L, customerCountForExpenses(entry.getValue()), 0L, true, "expense");
                })
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> expenseCustomerNodes(List<Expense> expenses, String parentId, boolean hasChildren) {
        return expenses.stream()
                .collect(Collectors.groupingBy(this::expenseCustomerKey, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> expenseCustomerLabel(entry.getValue().get(0))))
                .map(entry -> {
                    Expense sample = entry.getValue().get(0);
                    BigDecimal amount = sumExpenses(entry.getValue());
                    return node("expense_customer|" + parentId + "|" + entry.getKey(), parentId, "expense_customer", expenseCustomerLabel(sample), dateRangeLabel(entry.getValue()), amount,
                            amount, BigDecimal.ZERO, BigDecimal.ZERO, hasChildren ? (long) entry.getValue().size() : 0L, 0L, customerCountForExpenses(entry.getValue()), 0L, hasChildren, "customer");
                })
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> expenseCategoryNodes(List<Expense> expenses, String parentId) {
        boolean hasChildren = expenseTypeFromNode(parentId) != ExpenseType.GENERAL;
        return expenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getCategoryName()))
                .map(entry -> node("expense_category|" + parentId + "|" + entry.getKey().getId(), parentId, "expense_category", entry.getKey().getCategoryName(), dateRangeLabel(entry.getValue()), sumExpenses(entry.getValue()),
                        sumExpenses(entry.getValue()), BigDecimal.ZERO, BigDecimal.ZERO, expenseCategoryChildCount(expenseTypeFromNode(parentId), entry.getValue()), 0L, 0L, 0L, hasChildren, "expense"))
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> expenseInvoiceNodes(List<Expense> expenses, String parentId) {
        return expenses.stream()
                .collect(Collectors.groupingBy(this::expenseInvoiceKey, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> expenseInvoiceLabel(entry.getValue().get(0))))
                .map(entry -> {
                    BigDecimal amount = sumExpenses(entry.getValue());
                    return node("expense_invoice|" + parentId + "|" + entry.getKey(), parentId, "expense_invoice", expenseInvoiceLabel(entry.getValue().get(0)), dateRangeLabel(entry.getValue()), amount,
                            amount, BigDecimal.ZERO, BigDecimal.ZERO, customerCountForExpenses(entry.getValue()), 0L, customerCountForExpenses(entry.getValue()), 0L, true, "record");
                })
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> expenseRecordNodes(List<Expense> expenses, String parentId) {
        return expenses.stream()
                .sorted(Comparator.comparing(Expense::getExpenseDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(expense -> node("expense|" + expense.getId(), parentId, "expense", expenseTypeLabel(expense.getExpenseType()) + " - " + expense.getCategory().getCategoryName(), expenseRecordSubtitle(expense), expense.getAmount(),
                        expense.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0L, 0L, false, "expense"))
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> netTopLevelNodes(List<Payment> payments, List<Expense> expenses, DateRange range, String metric) {
        HierarchyDepth depth = hierarchyDepth(range);
        if (depth == HierarchyDepth.PERIOD) {
            return List.of(netPeriodNode(metric, "period|" + metric + "|" + iso(range.startDate()) + "|" + iso(range.endDate()), periodLabel(range), payments, expenses, metric, "net"));
        }
        if (depth == HierarchyDepth.MONTH) {
            YearMonth month = range.startDate() != null ? YearMonth.from(range.startDate()) : YearMonth.now();
            return List.of(netMonthNode(metric, month, payments, expenses, metric));
        }
        return java.util.stream.Stream.concat(
                        payments.stream().map(Payment::getPaymentDate).filter(Objects::nonNull).map(LocalDate::getYear),
                        expenses.stream().map(Expense::getExpenseDate).filter(Objects::nonNull).map(LocalDate::getYear)
                )
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(bucketYear -> netYearNode(metric, bucketYear, paymentsForYear(payments, bucketYear), expenses.stream()
                        .filter(expense -> expense.getExpenseDate() != null && expense.getExpenseDate().getYear() == bucketYear)
                        .toList(), metric))
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> netMonthNodes(List<Payment> payments, List<Expense> expenses, int year, String parentId) {
        return java.util.stream.Stream.concat(
                        payments.stream().map(Payment::getPaymentDate).filter(Objects::nonNull).map(YearMonth::from),
                        expenses.stream().map(Expense::getExpenseDate).filter(Objects::nonNull).map(YearMonth::from)
                )
                .filter(bucketMonth -> bucketMonth.getYear() == year)
                .distinct()
                .sorted()
                .map(bucketMonth -> netMonthNode("net-revenue", bucketMonth, paymentsForMonth(payments, bucketMonth), expenses.stream()
                        .filter(expense -> expense.getExpenseDate() != null && YearMonth.from(expense.getExpenseDate()).equals(bucketMonth))
                        .toList(), parentId))
                .filter(this::hasMeaningfulAmount)
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> monthNodes(List<Invoice> invoices, List<Payment> payments, int year) {
        return java.util.stream.Stream.concat(
                        invoices.stream()
                                .map(Invoice::getInvoiceDate)
                                .filter(Objects::nonNull)
                                .map(YearMonth::from),
                        payments.stream()
                                .map(Payment::getPaymentDate)
                                .filter(Objects::nonNull)
                                .map(YearMonth::from)
                )
                .filter(bucketMonth -> bucketMonth.getYear() == year)
                .distinct()
                .sorted()
                .map(bucketMonth -> {
                    List<Payment> monthPayments = paymentsForMonth(payments, bucketMonth);
                    List<Invoice> monthInvoices = includePaymentInvoices(invoices.stream()
                            .filter(invoice -> invoice.getInvoiceDate() != null && YearMonth.from(invoice.getInvoiceDate()).equals(bucketMonth))
                            .toList(), monthPayments);
                    return monthNode(bucketMonth, monthInvoices, monthPayments, "year|" + year);
                })
                .toList();
    }

    /*
     * Payment hierarchy is collection-first: a payment inside the selected period must stay visible
     * even when its invoice was created outside that period.
     */
    private List<Invoice> includePaymentInvoices(List<Invoice> invoices, List<Payment> payments) {
        Map<Long, Invoice> scoped = invoices.stream()
                .filter(invoice -> invoice != null && !invoice.isDeleted())
                .collect(Collectors.toMap(Invoice::getId, invoice -> invoice, (first, second) -> first, LinkedHashMap::new));
        payments.stream()
                .map(Payment::getInvoice)
                .filter(invoice -> invoice != null && !invoice.isDeleted())
                .forEach(invoice -> scoped.putIfAbsent(invoice.getId(), invoice));
        return List.copyOf(scoped.values());
    }

    private List<PaymentHierarchyNodeResponse> customerNodes(List<Invoice> invoices, List<Payment> payments, String parentId) {
        return invoices.stream()
                .collect(Collectors.groupingBy(Invoice::getCustomer, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .map(entry -> {
                    Customer customer = entry.getKey();
                    List<Invoice> customerInvoices = entry.getValue();
                    List<Payment> customerPayments = payments.stream()
                            .filter(payment -> payment.getCustomer().getId().equals(customer.getId()))
                            .toList();
                    BigDecimal total = sumInvoices(customerInvoices);
                    BigDecimal collected = sumPayments(customerPayments);
                    BigDecimal outstanding = scale(total.subtract(collected));
                    return node("customer|" + parentId + "|" + customer.getId(), parentId, "customer", customer.getName(), customer.getMobile(), collected,
                            total, collected, outstanding, (long) customerInvoices.size(), customerInvoices.size(), 1L, customerPayments.size(), true, "customer");
                })
                .toList();
    }

    private List<PaymentHierarchyNodeResponse> invoiceNodes(List<Invoice> invoices, List<Payment> payments, String parentId) {
        return invoices.stream()
                .sorted(Comparator.comparing(Invoice::getInvoiceDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(invoice -> {
                    List<Payment> invoicePayments = paymentsForInvoice(payments, invoice.getId());
                    BigDecimal total = scale(invoice.getTotalAmount());
                    BigDecimal collected = sumPayments(invoicePayments);
                    BigDecimal outstanding = scale(total.subtract(collected));
                    return node("invoice|" + invoice.getId(), parentId, "invoice", invoice.getInvoiceNo(), invoice.getCustomer().getName(), total,
                            total, collected, outstanding, 0L, 1L, 1L, invoicePayments.size(), false, "record");
                })
                .toList();
    }

    private PaymentHierarchyNodeResponse periodNode(String id, String label, List<Invoice> invoices, List<Payment> payments, String parentId, String tone) {
        BigDecimal total = sumInvoices(invoices);
        BigDecimal collected = sumPayments(payments);
        return node(id, parentId, "period", label, "Customer wise invoices", collected, total, collected, scale(total.subtract(collected)),
                customerCount(invoices), invoices.size(), customerCount(invoices), payments.size(), true, tone);
    }

    private PaymentHierarchyNodeResponse yearNode(int year, List<Invoice> invoices, List<Payment> payments, String parentId) {
        BigDecimal total = sumInvoices(invoices);
        BigDecimal collected = sumPayments(payments);
        return node("year|" + year, parentId, "year", String.valueOf(year), "Month wise invoices", collected, total, collected, scale(total.subtract(collected)),
                monthCount(invoices, payments), invoices.size(), customerCount(invoices), payments.size(), true, "year");
    }

    private PaymentHierarchyNodeResponse monthNode(YearMonth month, List<Invoice> invoices, List<Payment> payments, String parentId) {
        BigDecimal total = sumInvoices(invoices);
        BigDecimal collected = sumPayments(payments);
        return node("month|" + month.getYear() + "|" + month.getMonthValue(), parentId, "month", month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear(), "Customer wise invoices", collected, total, collected, scale(total.subtract(collected)),
                customerCount(invoices), invoices.size(), customerCount(invoices), payments.size(), true, "month");
    }

    private PaymentHierarchyNodeResponse metricPeriodNode(String metric, String id, String label, List<Invoice> invoices, List<Payment> payments, String parentId, String tone) {
        BigDecimal amount = sumInvoiceMetric(invoices, payments, metric);
        return node(id, parentId, "period", label, "Customer wise invoices", amount, sumInvoices(invoices), sumPayments(payments), sumOutstanding(invoices, payments),
                customerCount(invoices), invoices.size(), customerCount(invoices), payments.size(), true, tone);
    }

    private PaymentHierarchyNodeResponse metricYearNode(String metric, int year, List<Invoice> invoices, List<Payment> payments, String parentId) {
        BigDecimal amount = sumInvoiceMetric(invoices, payments, metric);
        return node("year|" + metric + "|" + year, parentId, "year", String.valueOf(year), "Month wise invoices", amount, sumInvoices(invoices), sumPayments(payments), sumOutstanding(invoices, payments),
                monthCount(invoices, payments), invoices.size(), customerCount(invoices), payments.size(), true, "year");
    }

    private PaymentHierarchyNodeResponse metricMonthNode(String metric, YearMonth month, List<Invoice> invoices, List<Payment> payments, String parentId) {
        BigDecimal amount = sumInvoiceMetric(invoices, payments, metric);
        return node("month|" + metric + "|" + month.getYear() + "|" + month.getMonthValue(), parentId, "month", month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear(), "Customer wise invoices", amount, sumInvoices(invoices), sumPayments(payments), sumOutstanding(invoices, payments),
                customerCount(invoices), invoices.size(), customerCount(invoices), payments.size(), true, "month");
    }

    private PaymentHierarchyNodeResponse expensePeriodNode(String metric, String id, String label, List<Expense> expenses, String parentId, String tone) {
        BigDecimal amount = sumExpenses(expenses);
        return node(id, parentId, "period", label, "Expense categories", amount, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                expenseTypeCount(expenses), 0L, 0L, 0L, true, tone);
    }

    private PaymentHierarchyNodeResponse expenseYearNode(String metric, int year, List<Expense> expenses, String parentId) {
        BigDecimal amount = sumExpenses(expenses);
        return node("year|" + metric + "|" + year, parentId, "year", String.valueOf(year), "Month wise expenses", amount, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                expenseMonthCount(expenses), 0L, 0L, 0L, true, "year");
    }

    private PaymentHierarchyNodeResponse expenseMonthNode(String metric, YearMonth month, List<Expense> expenses, String parentId) {
        BigDecimal amount = sumExpenses(expenses);
        return node("month|" + metric + "|" + month.getYear() + "|" + month.getMonthValue(), parentId, "month", month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear(), "Expense categories", amount, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                expenseTypeCount(expenses), 0L, 0L, 0L, true, "month");
    }

    private PaymentHierarchyNodeResponse netPeriodNode(String metric, String id, String label, List<Payment> payments, List<Expense> expenses, String parentId, String tone) {
        BigDecimal collected = sumPayments(payments);
        BigDecimal expense = sumExpenses(expenses);
        BigDecimal net = revenueCalculationService.netRevenue(collected, expense);
        return node(id, parentId, "period", label, "Collection minus expense", net, net, collected, expense, 0L, 0L, 0L, payments.size(), false, tone);
    }

    private PaymentHierarchyNodeResponse netYearNode(String metric, int year, List<Payment> payments, List<Expense> expenses, String parentId) {
        BigDecimal collected = sumPayments(payments);
        BigDecimal expense = sumExpenses(expenses);
        BigDecimal net = revenueCalculationService.netRevenue(collected, expense);
        return node("year|" + metric + "|" + year, parentId, "year", String.valueOf(year), "Month wise net revenue", net, net, collected, expense, netMonthCount(payments, expenses), 0L, 0L, payments.size(), true, "year");
    }

    private PaymentHierarchyNodeResponse netMonthNode(String metric, YearMonth month, List<Payment> payments, List<Expense> expenses, String parentId) {
        BigDecimal collected = sumPayments(payments);
        BigDecimal expense = sumExpenses(expenses);
        BigDecimal net = revenueCalculationService.netRevenue(collected, expense);
        return node("month|" + metric + "|" + month.getYear() + "|" + month.getMonthValue(), parentId, "month", month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear(), "Collection minus expense", net, net, collected, expense, 0L, 0L, 0L, payments.size(), false, "month");
    }

    private PaymentHierarchyNodeResponse node(String id,
                                              String parentId,
                                              String type,
                                              String label,
                                              String subtitle,
                                              BigDecimal amount,
                                              BigDecimal totalAmount,
                                              BigDecimal collectedAmount,
                                              BigDecimal outstandingAmount,
                                              Long count,
                                              long invoiceCount,
                                              long customerCount,
                                              long collectionCount,
                                              boolean hasChildren,
                                              String tone) {
        return PaymentHierarchyNodeResponse.builder()
                .id(id)
                .parentId(parentId)
                .type(type)
                .label(label)
                .subtitle(subtitle)
                .amount(scale(amount))
                .totalAmount(scale(totalAmount))
                .collectedAmount(scale(collectedAmount))
                .outstandingAmount(scale(outstandingAmount))
                .count(count)
                .invoiceCount(invoiceCount)
                .customerCount(customerCount)
                .collectionCount(collectionCount)
                .hasChildren(hasChildren)
                .tone(tone)
                .build();
    }

    private List<Invoice> invoicesForScope(List<Invoice> invoices, String nodeId) {
        if (nodeId == null) {
            return invoices;
        }
        String[] parts = nodeId.split("\\|");
        if ("year".equals(parts[0]) && parts.length > 1) {
            int year = Integer.parseInt(parts[parts.length > 2 ? 2 : 1]);
            return invoices.stream().filter(invoice -> invoice.getInvoiceDate() != null && invoice.getInvoiceDate().getYear() == year).toList();
        }
        if ("month".equals(parts[0]) && parts.length > 2) {
            YearMonth month = parts.length > 3
                    ? YearMonth.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))
                    : YearMonth.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return invoices.stream().filter(invoice -> invoice.getInvoiceDate() != null && YearMonth.from(invoice.getInvoiceDate()).equals(month)).toList();
        }
        if ("period".equals(parts[0]) && parts.length > 2) {
            DateRange range = parts.length > 3
                    ? new DateRange(parseDate(parts[2]), parseDate(parts[3]))
                    : new DateRange(parseDate(parts[1]), parseDate(parts[2]));
            return invoices.stream().filter(invoice -> isWithinRange(invoice.getInvoiceDate(), range)).toList();
        }
        return invoices;
    }

    private List<Payment> paymentsForScope(List<Payment> payments, String nodeId) {
        if (nodeId == null) {
            return payments;
        }
        String[] parts = nodeId.split("\\|");
        if ("year".equals(parts[0]) && parts.length > 1) {
            return paymentsForYear(payments, Integer.parseInt(parts[parts.length > 2 ? 2 : 1]));
        }
        if ("month".equals(parts[0]) && parts.length > 2) {
            YearMonth month = parts.length > 3
                    ? YearMonth.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))
                    : YearMonth.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return paymentsForMonth(payments, month);
        }
        if ("period".equals(parts[0]) && parts.length > 2) {
            DateRange range = parts.length > 3
                    ? new DateRange(parseDate(parts[2]), parseDate(parts[3]))
                    : new DateRange(parseDate(parts[1]), parseDate(parts[2]));
            return payments.stream().filter(payment -> isWithinRange(payment.getPaymentDate(), range)).toList();
        }
        return payments;
    }

    private List<Expense> expensesForScope(List<Expense> expenses, String nodeId) {
        if (nodeId == null) {
            return expenses;
        }
        String[] parts = nodeId.split("\\|");
        if ("year".equals(parts[0]) && parts.length > 1) {
            int year = Integer.parseInt(parts[parts.length > 2 ? 2 : 1]);
            return expenses.stream().filter(expense -> expense.getExpenseDate() != null && expense.getExpenseDate().getYear() == year).toList();
        }
        if ("month".equals(parts[0]) && parts.length > 2) {
            YearMonth month = parts.length > 3
                    ? YearMonth.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))
                    : YearMonth.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return expenses.stream().filter(expense -> expense.getExpenseDate() != null && YearMonth.from(expense.getExpenseDate()).equals(month)).toList();
        }
        if ("period".equals(parts[0]) && parts.length > 2) {
            DateRange range = parts.length > 3
                    ? new DateRange(parseDate(parts[2]), parseDate(parts[3]))
                    : new DateRange(parseDate(parts[1]), parseDate(parts[2]));
            return expenses.stream().filter(expense -> isWithinRange(expense.getExpenseDate(), range)).toList();
        }
        return expenses;
    }

    private List<Expense> expensesForCategoryScope(List<Expense> expenses, String nodeId) {
        long categoryId = customerIdFromNode(nodeId);
        String parentId = parentIdFromPrefixedNode("expense_category|", nodeId);
        List<Expense> scopedExpenses = expensesForExpenseParentScope(expenses, parentId);
        return scopedExpenses.stream()
                .filter(expense -> expense.getCategory().getId().equals(categoryId))
                .toList();
    }

    private List<Expense> expensesForExpenseTypeScope(List<Expense> expenses, String nodeId) {
        ExpenseType expenseType = expenseTypeFromNode(nodeId);
        String parentId = parentIdFromPrefixedNode("expense_type|", nodeId);
        return expensesForScope(expenses, parentId).stream()
                .filter(expense -> expense.getExpenseType() == expenseType)
                .toList();
    }

    private List<Expense> expensesForExpenseParentScope(List<Expense> expenses, String parentId) {
        if (parentId.startsWith("expense_type|")) {
            return expensesForExpenseTypeScope(expenses, parentId);
        }
        if (parentId.startsWith("expense_category|")) {
            return expensesForCategoryScope(expenses, parentId);
        }
        if (parentId.startsWith("expense_invoice|")) {
            return expensesForExpenseInvoiceScope(expenses, parentId);
        }
        if (parentId.startsWith("expense_customer|")) {
            return expensesForExpenseCustomerScope(expenses, parentId);
        }
        return expensesForScope(expenses, parentId);
    }

    private List<Expense> expensesForExpenseCustomerScope(List<Expense> expenses, String nodeId) {
        String customerKey = lastNodePart(nodeId);
        String parentId = parentIdFromPrefixedNode("expense_customer|", nodeId);
        return expensesForExpenseParentScope(expenses, parentId).stream()
                .filter(expense -> expenseCustomerKey(expense).equals(customerKey))
                .toList();
    }

    private List<Expense> expensesForExpenseInvoiceScope(List<Expense> expenses, String nodeId) {
        String invoiceKey = lastNodePart(nodeId);
        String parentId = parentIdFromPrefixedNode("expense_invoice|", nodeId);
        return expensesForExpenseParentScope(expenses, parentId).stream()
                .filter(expense -> expenseInvoiceKey(expense).equals(invoiceKey))
                .toList();
    }

    private List<Invoice> invoicesForCustomerScope(List<Invoice> invoices, String nodeId) {
        long customerId = customerIdFromNode(nodeId);
        String parentId = parentIdFromCustomerNode(nodeId);
        return invoicesForScope(invoices, parentId).stream()
                .filter(invoice -> invoice.getCustomer().getId().equals(customerId))
                .toList();
    }

    private List<Payment> paymentsForCustomerScope(List<Payment> payments, String nodeId) {
        long customerId = customerIdFromNode(nodeId);
        String parentId = parentIdFromCustomerNode(nodeId);
        return paymentsForScope(payments, parentId).stream()
                .filter(payment -> payment.getCustomer().getId().equals(customerId))
                .toList();
    }

    private List<Invoice> invoicesForYear(List<Invoice> invoices, String nodeId) {
        return invoicesForScope(invoices, nodeId);
    }

    private List<Payment> paymentsForYear(List<Payment> payments, String nodeId) {
        return paymentsForScope(payments, nodeId);
    }

    private List<Payment> paymentsForYear(List<Payment> payments, int year) {
        return payments.stream().filter(payment -> payment.getPaymentDate() != null && payment.getPaymentDate().getYear() == year).toList();
    }

    private List<Payment> paymentsForMonth(List<Payment> payments, YearMonth month) {
        return payments.stream().filter(payment -> payment.getPaymentDate() != null && YearMonth.from(payment.getPaymentDate()).equals(month)).toList();
    }

    private List<Payment> paymentsForInvoice(List<Payment> payments, Long invoiceId) {
        if (invoiceId == null) {
            return List.of();
        }
        return payments.stream()
                .filter(payment -> payment.getInvoice() != null && payment.getInvoice().getId().equals(invoiceId))
                .toList();
    }

    private int yearFromNode(String nodeId) {
        String[] parts = nodeId == null ? new String[0] : nodeId.split("\\|");
        return parts.length > 2 ? Integer.parseInt(parts[2]) : (parts.length > 1 ? Integer.parseInt(parts[1]) : LocalDate.now().getYear());
    }

    private Long invoiceIdFromNode(String nodeId) {
        String[] parts = nodeId == null ? new String[0] : nodeId.split("\\|");
        return parts.length > 1 && "invoice".equals(parts[0]) ? Long.parseLong(parts[parts.length - 1]) : null;
    }

    private String metricFromNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "total-sales";
        }
        String[] parts = nodeId.split("\\|");
        if ("customer".equals(parts[0])) {
            return metricFromNode(parentIdFromCustomerNode(nodeId));
        }
        if (("year".equals(parts[0]) || "month".equals(parts[0]) || "period".equals(parts[0]) || "invoice".equals(parts[0])) && parts.length > 2) {
            return parts[1];
        }
        return nodeId;
    }

    private ExpenseType expenseTypeFromNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return ExpenseType.GENERAL;
        }
        String[] parts = nodeId.split("\\|");
        for (int index = parts.length - 1; index >= 0; index--) {
            try {
                return ExpenseType.valueOf(parts[index]);
            } catch (IllegalArgumentException ignored) {
                // Keep walking up the encoded hierarchy id until we find the expense type segment.
            }
        }
        return ExpenseType.GENERAL;
    }

    private long customerIdFromNode(String nodeId) {
        String[] parts = nodeId.split("\\|");
        return Long.parseLong(parts[parts.length - 1]);
    }

    private String parentIdFromCustomerNode(String nodeId) {
        String prefix = "customer|";
        String withoutPrefix = nodeId.startsWith(prefix) ? nodeId.substring(prefix.length()) : nodeId;
        int lastSeparator = withoutPrefix.lastIndexOf('|');
        return lastSeparator > 0 ? withoutPrefix.substring(0, lastSeparator) : withoutPrefix;
    }

    private String parentIdFromPrefixedNode(String prefix, String nodeId) {
        String withoutPrefix = nodeId != null && nodeId.startsWith(prefix) ? nodeId.substring(prefix.length()) : nodeId;
        int lastSeparator = withoutPrefix == null ? -1 : withoutPrefix.lastIndexOf('|');
        return lastSeparator > 0 ? withoutPrefix.substring(0, lastSeparator) : "";
    }

    private String lastNodePart(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        int lastSeparator = nodeId.lastIndexOf('|');
        return lastSeparator >= 0 ? nodeId.substring(lastSeparator + 1) : nodeId;
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
                .filter(payment -> payment.getInvoice() == null || !payment.getInvoice().isDeleted())
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

    private Summary summary(List<Invoice> invoices, List<Payment> payments, List<Expense> expenses) {
        BigDecimal receivable = sumInvoices(invoices);
        BigDecimal collected = sumPayments(payments);
        BigDecimal outstanding = scale(receivable.subtract(collected));
        BigDecimal expense = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(scale(receivable), scale(collected), scale(outstanding), scale(expense), revenueCalculationService.netRevenue(collected, expense), (long) invoices.size(), (long) payments.size(), invoices.stream().filter(invoice -> scale(invoice.getBalanceAmount()).compareTo(BigDecimal.ZERO) > 0).count(), (long) expenses.size());
    }

    private BigDecimal sumInvoices(List<Invoice> invoices) {
        return scale(invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumPayments(List<Payment> payments) {
        return scale(payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumExpenses(List<Expense> expenses) {
        return scale(expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumOutstanding(List<Invoice> invoices, List<Payment> payments) {
        return scale(invoices.stream()
                .map(invoice -> invoiceOutstanding(invoice, paymentsForInvoice(payments, invoice.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumInvoiceMetric(List<Invoice> invoices, List<Payment> payments, String metric) {
        return scale(invoices.stream()
                .map(invoice -> invoiceMetricAmount(invoice, paymentsForInvoice(payments, invoice.getId()), metric))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal invoiceMetricAmount(Invoice invoice, List<Payment> payments, String metric) {
        if ("total-collection".equals(metric)) {
            return sumPayments(payments);
        }
        if ("total-outstanding".equals(metric)) {
            return invoiceOutstanding(invoice, payments);
        }
        return scale(invoice.getTotalAmount());
    }

    private BigDecimal invoiceOutstanding(Invoice invoice, List<Payment> payments) {
        BigDecimal balance = invoice.getBalanceAmount();
        if (balance != null) {
            return scale(balance);
        }
        return scale(scale(invoice.getTotalAmount()).subtract(sumPayments(payments)));
    }

    private List<Payment> metricPayments(String metric, List<Payment> payments) {
        return payments.stream()
                .filter(payment -> !"total-outstanding".equals(metric) || (payment.getInvoice() != null && invoiceOutstanding(payment.getInvoice(), paymentsForInvoice(payments, payment.getInvoice().getId())).compareTo(BigDecimal.ZERO) > 0))
                .toList();
    }

    private List<Invoice> metricInvoices(String metric, List<Invoice> invoices, List<Payment> payments) {
        List<Invoice> scoped = "total-collection".equals(metric)
                ? includePaymentInvoices(List.of(), payments)
                : includePaymentInvoices(invoices, payments);
        return scoped.stream()
                .filter(invoice -> {
                    BigDecimal amount = invoiceMetricAmount(invoice, paymentsForInvoice(payments, invoice.getId()), metric);
                    return amount.compareTo(BigDecimal.ZERO) != 0;
                })
                .toList();
    }

    private boolean hasMeaningfulAmount(PaymentHierarchyNodeResponse node) {
        return node != null && node.getAmount() != null && node.getAmount().compareTo(BigDecimal.ZERO) != 0;
    }

    private long customerCount(List<Invoice> invoices) {
        return invoices.stream().map(invoice -> invoice.getCustomer().getId()).distinct().count();
    }

    private long monthCount(List<Invoice> invoices, List<Payment> payments) {
        return java.util.stream.Stream.concat(
                        invoices.stream()
                                .map(Invoice::getInvoiceDate)
                                .filter(Objects::nonNull)
                                .map(YearMonth::from),
                        payments.stream()
                                .map(Payment::getPaymentDate)
                                .filter(Objects::nonNull)
                                .map(YearMonth::from)
                )
                .distinct()
                .count();
    }

    private long expenseMonthCount(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getExpenseDate)
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .distinct()
                .count();
    }

    private long expenseTypeCount(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getExpenseType)
                .distinct()
                .count();
    }

    private long expenseCategoryCount(List<Expense> expenses) {
        return expenses.stream()
                .map(expense -> expense.getCategory().getId())
                .distinct()
                .count();
    }

    private long expenseCategoryChildCount(ExpenseType expenseType, List<Expense> expenses) {
        if (expenseType == ExpenseType.INVOICE_RELATED) {
            return expenses.stream().map(this::expenseInvoiceKey).distinct().count();
        }
        if (expenseType == ExpenseType.CUSTOMER_RELATED) {
            return expenses.stream().map(this::expenseCustomerKey).distinct().count();
        }
        return 0L;
    }

    private long netMonthCount(List<Payment> payments, List<Expense> expenses) {
        return java.util.stream.Stream.concat(
                        payments.stream()
                                .map(Payment::getPaymentDate)
                                .filter(Objects::nonNull)
                                .map(YearMonth::from),
                        expenses.stream()
                                .map(Expense::getExpenseDate)
                                .filter(Objects::nonNull)
                                .map(YearMonth::from)
                )
                .distinct()
                .count();
    }

    private long customerCountForExpenses(List<Expense> expenses) {
        return expenses.stream()
                .map(this::expenseCustomerKey)
                .filter(key -> !"general".equals(key))
                .distinct()
                .count();
    }

    private String expenseCustomerKey(Expense expense) {
        Customer customer = expenseCustomer(expense);
        return customer == null ? "general" : String.valueOf(customer.getId());
    }

    private String expenseCustomerLabel(Expense expense) {
        Customer customer = expenseCustomer(expense);
        return customer == null ? "General Expense" : customer.getName();
    }

    private String expenseCustomerSubtitle(Expense expense) {
        Customer customer = expenseCustomer(expense);
        return customer == null ? "No customer linked" : customer.getMobile();
    }

    private String expenseInvoiceKey(Expense expense) {
        return expense.getInvoice() == null ? "no-invoice" : String.valueOf(expense.getInvoice().getId());
    }

    private String expenseInvoiceLabel(Expense expense) {
        return expense.getInvoice() == null ? "No Invoice" : expense.getInvoice().getInvoiceNo();
    }

    private String expenseTypeLabel(ExpenseType expenseType) {
        if (expenseType == null) {
            return "General";
        }
        return java.util.Arrays.stream(expenseType.name().split("_"))
                .map(part -> part.charAt(0) + part.substring(1).toLowerCase(Locale.ENGLISH))
                .collect(Collectors.joining(" "));
    }

    private String dateRangeLabel(List<Expense> expenses) {
        List<LocalDate> dates = expenses.stream()
                .map(Expense::getExpenseDate)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (dates.isEmpty()) {
            return "--";
        }
        String start = dates.get(0).format(INDIAN_DATE_FORMATTER);
        String end = dates.get(dates.size() - 1).format(INDIAN_DATE_FORMATTER);
        return start.equals(end) ? start : start + " - " + end;
    }

    private Customer expenseCustomer(Expense expense) {
        if (expense.getCustomer() != null) {
            return expense.getCustomer();
        }
        if (expense.getInvoice() != null && expense.getInvoice().getCustomer() != null) {
            return expense.getInvoice().getCustomer();
        }
        return null;
    }

    private String expenseRecordSubtitle(Expense expense) {
        String date = expense.getExpenseDate() == null ? "--" : expense.getExpenseDate().format(INDIAN_DATE_FORMATTER);
        String description = expense.getDescription();
        return description == null || description.isBlank() ? date : date + " - " + description;
    }

    private boolean matchesCollector(Payment payment, String normalizedCollector) {
        String rawCreatedBy = payment.getCreatedBy();
        String displayName = auditNameResolver.displayName(rawCreatedBy);
        return (rawCreatedBy != null && rawCreatedBy.equalsIgnoreCase(normalizedCollector))
                || (displayName != null && displayName.equalsIgnoreCase(normalizedCollector));
    }

    private HierarchyDepth hierarchyDepth(DateRange range) {
        if (range.startDate() == null || range.endDate() == null) {
            return HierarchyDepth.YEAR;
        }
        if (Objects.equals(range.startDate(), range.endDate()) || isCurrentWeekRange(range)) {
            return HierarchyDepth.PERIOD;
        }
        if (YearMonth.from(range.startDate()).equals(YearMonth.from(range.endDate()))) {
            return HierarchyDepth.MONTH;
        }
        return HierarchyDepth.YEAR;
    }

    private String periodLabel(DateRange range) {
        if (Objects.equals(range.startDate(), range.endDate())) {
            if (range.startDate() == null) {
                return "Selected Period";
            }
            LocalDate today = LocalDate.now();
            if (range.startDate().equals(today)) {
                return "Today";
            }
            if (range.startDate().equals(today.minusDays(1))) {
                return "Yesterday";
            }
            return range.startDate().toString();
        }
        if (isCurrentWeekRange(range)) {
            return "This Week";
        }
        return "Selected Period";
    }

    private boolean isCurrentWeekRange(DateRange range) {
        if (range.startDate() == null || range.endDate() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        return range.startDate().equals(startOfWeek) && range.endDate().equals(today);
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
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                return new DateRange(endDate, startDate);
            }
            return new DateRange(startDate, endDate);
        }
        if (financialYear != null) {
            return new DateRange(LocalDate.of(financialYear, 4, 1), LocalDate.of(financialYear + 1, 3, 31));
        }
        return new DateRange(null, null);
    }

    private String normalizeMode(String mode) {
        return mode == null || mode.isBlank() ? null : mode.trim().toUpperCase(Locale.ENGLISH);
    }

    private String label(String mode) {
        if (mode == null || mode.isBlank()) {
            return "--";
        }
        return java.util.Arrays.stream(mode.trim().split("_"))
                .map(part -> part.charAt(0) + part.substring(1).toLowerCase(Locale.ENGLISH))
                .collect(Collectors.joining(" "));
    }

    private String iso(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private enum HierarchyDepth {
        PERIOD,
        MONTH,
        YEAR
    }

    private record Summary(BigDecimal totalReceivable, BigDecimal totalCollected, BigDecimal totalOutstanding, BigDecimal totalExpense, BigDecimal netRevenue, Long invoiceCount, Long paymentCount, Long outstandingInvoiceCount, Long expenseCount) {
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
