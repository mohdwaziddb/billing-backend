package com.billing.ai.tool;

import com.billing.dto.PageResponse;
import com.billing.dto.analytics.AnalyticsSummaryResponse;
import com.billing.dto.analytics.CustomerDueResponse;
import com.billing.dto.analytics.LowStockProductResponse;
import com.billing.dto.analytics.MetricPointResponse;
import com.billing.dto.analytics.OwnerAnalyticsResponse;
import com.billing.dto.analytics.SalesChartPointResponse;
import com.billing.dto.analytics.TopSellingProductResponse;
import com.billing.dto.customer.CustomerRequest;
import com.billing.dto.customer.CustomerResponse;
import com.billing.dto.invoice.InvoiceItemRequest;
import com.billing.dto.invoice.InvoiceItemResponse;
import com.billing.dto.invoice.InvoiceRequest;
import com.billing.dto.invoice.InvoiceResponse;
import com.billing.dto.payment.PaymentRequest;
import com.billing.dto.payment.PaymentResponse;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Payment;
import com.billing.entity.Product;
import com.billing.repository.CustomerRepository;
import com.billing.repository.PaymentRepository;
import com.billing.repository.ProductRepository;
import com.billing.service.AccessControlService;
import com.billing.service.AnalyticsService;
import com.billing.service.CustomerService;
import com.billing.service.InvoiceService;
import com.billing.service.PaymentService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BillingAiToolService {

    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);

    private final CustomerService customerService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final AnalyticsService analyticsService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final AccessControlService accessControlService;

    @Tool(name = "get_sales_summary", description = "Get the current business summary: today's sales, today's collection, this month's sales and collection, total sales, total collection, total outstanding balance, total customers, total invoices, number of customers with dues, and number of low-stock products.")
    public String getSalesSummary() {
        try {
            LocalDate today = LocalDate.now();
            AnalyticsSummaryResponse todayStats = analyticsService.summary("x", today, today);
            AnalyticsSummaryResponse totals = analyticsService.summary("x", MIN_DATE, today);
            AnalyticsSummaryResponse thisMonth = analyticsService.summary("x", today.withDayOfMonth(1), today);
            StringBuilder sb = new StringBuilder();
            sb.append("Business summary:\n");
            sb.append("- Today's sales: ").append(money(todayStats.getTodaySales())).append("\n");
            sb.append("- Today's collection: ").append(money(todayStats.getTotalCollection())).append("\n");
            sb.append("- This month sales: ").append(money(thisMonth.getTotalSales())).append("\n");
            sb.append("- This month collection: ").append(money(thisMonth.getTotalCollection())).append("\n");
            sb.append("- Total sales: ").append(money(totals.getTotalSales())).append("\n");
            sb.append("- Total collection: ").append(money(totals.getTotalCollection())).append("\n");
            sb.append("- Total outstanding balance: ").append(money(totals.getTotalOutstandingBalance())).append("\n");
            sb.append("- Total customers: ").append(customerRepository.countByCompany(accessControlService.getCurrentCompany())).append("\n");
            sb.append("- Total invoices: ").append(totals.getTotalInvoices()).append("\n");
            sb.append("- Customers with dues: ").append(totals.getDueCustomers()).append("\n");
            sb.append("- Low-stock products: ").append(totals.getLowStockProducts()).append("\n");
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to fetch summary: " + safeMessage(ex);
        }
    }

    @Tool(name = "get_daily_collection_chart", description = "Get day-wise sales and collection amounts for the CURRENT month (today's month). Use this when the user asks for a graph or chart of sales or collection. This tool always uses the current month, so do not ask the user for a month.")
    public String getDailyCollectionChart() {
        try {
            LocalDate today = LocalDate.now();
            int targetMonth = today.getMonthValue();
            int targetYear = today.getYear();
            List<SalesChartPointResponse> salesPoints = analyticsService.dayWiseSales("x", targetYear, targetMonth);
            Map<Integer, BigDecimal> collectionByDay = collectionByDay(targetYear, targetMonth);
            YearMonth selected = YearMonth.of(targetYear, targetMonth);
            StringBuilder sb = new StringBuilder();
            sb.append("Day-wise sales and collection for ").append(selected).append(" (day: sales / collection):\n");
            SalesChartPointResponse bestSalesDay = null;
            int bestCollectionDay = -1;
            BigDecimal bestCollectionAmount = BigDecimal.ZERO;
            for (SalesChartPointResponse point : salesPoints) {
                int day = point.getIndex();
                BigDecimal collection = collectionByDay.getOrDefault(day, BigDecimal.ZERO);
                if (bestSalesDay == null || point.getSalesAmount().compareTo(bestSalesDay.getSalesAmount()) > 0) {
                    bestSalesDay = point;
                }
                if (collection.compareTo(bestCollectionAmount) > 0) {
                    bestCollectionAmount = collection;
                    bestCollectionDay = day;
                }
                if (point.getSalesAmount().signum() > 0 || collection.signum() > 0) {
                    sb.append("- Day ").append(day).append(": sales ").append(money(point.getSalesAmount()))
                            .append(", collection ").append(money(collection)).append("\n");
                }
            }
            BigDecimal totalSales = salesPoints.stream()
                    .map(SalesChartPointResponse::getSalesAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCollection = collectionByDay.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sb.append("Monthly total: sales ").append(money(totalSales))
                    .append(", collection ").append(money(totalCollection)).append("\n");
            if (bestSalesDay != null && bestSalesDay.getSalesAmount().signum() > 0) {
                sb.append("Highest sales day: Day ").append(bestSalesDay.getIndex())
                        .append(" (sales ").append(money(bestSalesDay.getSalesAmount())).append(")\n");
            }
            if (bestCollectionDay > 0) {
                sb.append("Highest collection day: Day ").append(bestCollectionDay)
                        .append(" (collection ").append(money(bestCollectionAmount)).append(")\n");
            }
            sb.append("Note: Days not listed have zero sales and zero collection.");
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to fetch chart data: " + safeMessage(ex);
        }
    }

    @Tool(name = "find_customer", description = "Search active customers by name, mobile number or email. Returns customer id, name, mobile and outstanding balance. Use the id returned here when creating invoices or payments.")
    public String findCustomer(
            @ToolParam(description = "Search text: customer name, 10-digit mobile number or email.", required = true) String query) {
        try {
            if (query == null || query.isBlank()) {
                return "Please provide a search text.";
            }
            Company company = accessControlService.getCurrentCompany();
            List<Customer> customers = customerRepository.findAllByCompanyWithFilters(company, true, query.trim());
            if (customers.isEmpty()) {
                return "No customer found matching: " + query;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Matching customers:\n");
            int limit = Math.min(customers.size(), 5);
            for (int i = 0; i < limit; i++) {
                Customer c = customers.get(i);
                sb.append("- id=").append(c.getId())
                        .append(", name=").append(c.getName())
                        .append(", mobile=").append(c.getMobile())
                        .append(", outstanding=").append(money(c.getCurrentBalance()))
                        .append("\n");
            }
            if (customers.size() > limit) {
                sb.append("...and ").append(customers.size() - limit).append(" more.\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to search customers: " + safeMessage(ex);
        }
    }

    @Tool(name = "find_product", description = "Search active products by name or SKU. Returns product id, name and SKU. Use the id returned here when creating invoices.")
    public String findProduct(
            @ToolParam(description = "Search text: product name or SKU.", required = true) String query) {
        try {
            if (query == null || query.isBlank()) {
                return "Please provide a search text.";
            }
            Company company = accessControlService.getCurrentCompany();
            List<Product> products = productRepository.findAllByCompanyWithFilters(company, true, null, null, query.trim());
            if (products.isEmpty()) {
                return "No product found matching: " + query;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Matching products:\n");
            int limit = Math.min(products.size(), 5);
            for (int i = 0; i < limit; i++) {
                Product p = products.get(i);
                sb.append("- id=").append(p.getId())
                        .append(", name=").append(p.getName())
                        .append(", sku=").append(p.getSku())
                        .append("\n");
            }
            if (products.size() > limit) {
                sb.append("...and ").append(products.size() - limit).append(" more.\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to search products: " + safeMessage(ex);
        }
    }

    @Tool(name = "create_customer", description = "Create a new customer. The mobile number must be exactly 10 digits. Only call this after the user confirms the details.")
    public String createCustomer(
            @ToolParam(description = "Customer full name.", required = true) String name,
            @ToolParam(description = "10-digit mobile number.", required = true) String mobile,
            @ToolParam(description = "Email address, optional.") String email,
            @ToolParam(description = "GST number (GSTIN), optional. Required if the customer is GST registered.") String gstin,
            @ToolParam(description = "Whether the customer is GST registered: true or false.") Boolean gstRegistered) {
        if (name == null || name.isBlank()) {
            return "Customer name is required.";
        }
        if (mobile == null || !mobile.trim().matches("\\d{10}")) {
            return "Invalid mobile number: it must be exactly 10 digits.";
        }
        CustomerRequest request = new CustomerRequest();
        request.setName(name.trim());
        request.setMobile(mobile.trim());
        request.setEmail(blankToNull(email));
        request.setGstin(blankToNull(gstin));
        request.setGstRegistered(Boolean.TRUE.equals(gstRegistered));
        request.setActive(true);
        try {
            CustomerResponse saved = customerService.create("x", request);
            return "Customer created successfully: id=" + saved.getId()
                    + ", name=" + saved.getName()
                    + ", mobile=" + saved.getMobile();
        } catch (Exception ex) {
            return "Customer create failed: " + safeMessage(ex);
        }
    }

    @Tool(name = "create_invoice", description = "Create a new invoice/bill for an existing customer. Items must each have a productId (use find_product) and qty (minimum 1). Only call this after the user confirms the items, quantities and amounts.")
    public String createInvoice(
            @ToolParam(description = "Customer id. Get it from find_customer first.", required = true) Long customerId,
            @ToolParam(description = "List of items. Each item needs productId and qty, price is optional.", required = true) List<InvoiceItemInput> items,
            @ToolParam(description = "Invoice date in YYYY-MM-DD format. Defaults to today.") String invoiceDate,
            @ToolParam(description = "Discount amount, optional.") BigDecimal discountAmount,
            @ToolParam(description = "Amount paid at the time of billing, optional.") BigDecimal paidAmount,
            @ToolParam(description = "Payment mode when paid amount is given, for example cash, upi, card or bank.") String paymentMode) {
        if (customerId == null) {
            return "Customer id is required.";
        }
        if (items == null || items.isEmpty()) {
            return "Invoice must have at least one item.";
        }
        for (InvoiceItemInput item : items) {
            if (item.getProductId() == null || item.getQty() == null || item.getQty() < 1) {
                return "Every item needs a valid productId and qty of at least 1.";
            }
        }
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId(customerId);
        request.setInvoiceDate(parseDate(invoiceDate, LocalDate.now()));
        request.setDiscountAmount(discountAmount);
        request.setPaidAmount(paidAmount);
        request.setPaymentMode(blankToNull(paymentMode));
        List<InvoiceItemRequest> itemRequests = items.stream().map(item -> {
            InvoiceItemRequest ir = new InvoiceItemRequest();
            ir.setProductId(item.getProductId());
            ir.setQty(item.getQty());
            ir.setPrice(item.getPrice());
            return ir;
        }).toList();
        request.setItems(itemRequests);
        try {
            InvoiceResponse saved = invoiceService.create("x", request);
            return "Invoice created successfully: id=" + saved.getId()
                    + ", invoiceNo=" + saved.getInvoiceNo()
                    + ", total=" + money(saved.getTotalAmount())
                    + ", paid=" + money(saved.getPaidAmount());
        } catch (Exception ex) {
            return "Invoice create failed: " + safeMessage(ex);
        }
    }

    @Tool(name = "create_payment", description = "Record a payment (collection) received from a customer. The amount must be greater than zero. Only call this after the user confirms the amount and mode.")
    public String createPayment(
            @ToolParam(description = "Customer id. Get it from find_customer first.", required = true) Long customerId,
            @ToolParam(description = "Payment amount, must be greater than zero.", required = true) BigDecimal amount,
            @ToolParam(description = "Payment mode, for example cash, upi, card or bank.", required = true) String mode,
            @ToolParam(description = "Payment date in YYYY-MM-DD format. Defaults to today.") String paymentDate,
            @ToolParam(description = "Invoice id the payment is for, optional.") Long invoiceId,
            @ToolParam(description = "Remarks, optional.") String remarks) {
        if (customerId == null) {
            return "Customer id is required.";
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Payment amount must be greater than zero.";
        }
        if (mode == null || mode.isBlank()) {
            return "Payment mode is required.";
        }
        PaymentRequest request = new PaymentRequest();
        request.setCustomerId(customerId);
        request.setInvoiceId(invoiceId);
        request.setAmount(amount);
        request.setPaymentDate(parseDate(paymentDate, LocalDate.now()));
        request.setMode(mode.trim());
        request.setRemarks(blankToNull(remarks));
        try {
            PaymentResponse saved = paymentService.create("x", request);
            return "Payment recorded successfully: id=" + saved.getId()
                    + ", customer=" + saved.getCustomerName()
                    + ", amount=" + money(saved.getAmount())
                    + ", mode=" + saved.getMode()
                    + ", date=" + saved.getPaymentDate();
        } catch (Exception ex) {
            return "Payment create failed: " + safeMessage(ex);
        }
    }

    @Tool(name = "get_customer_invoices", description = "List the invoices (bills) of an existing customer, newest first. Returns invoice id, invoice number, date, total, paid, balance and payment status for each invoice. Use this whenever the user asks to see a customer's invoices, bills, purchase history, or how much a customer has paid or still owes.")
    public String getCustomerInvoices(
            @ToolParam(description = "Customer id. Get it from find_customer first.", required = true) Long customerId,
            @ToolParam(description = "Maximum number of invoices to show, between 1 and 10. Defaults to 5.", required = false) Integer limit) {
        if (customerId == null) {
            return "Customer id is required. Use find_customer first to get the customer id.";
        }
        int safeLimit = limit == null ? 5 : Math.max(1, Math.min(limit, 10));
        try {
            List<InvoiceResponse> invoices = invoiceService.list("x", customerId);
            if (invoices.isEmpty()) {
                return "No invoices found for this customer.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Invoices of the customer:\n");
            int shown = Math.min(invoices.size(), safeLimit);
            for (int i = 0; i < shown; i++) {
                InvoiceResponse inv = invoices.get(i);
                sb.append("- id=").append(inv.getId())
                        .append(", invoiceNo=").append(inv.getInvoiceNo())
                        .append(", date=").append(inv.getInvoiceDate())
                        .append(", total=").append(money(inv.getTotalAmount()))
                        .append(", paid=").append(money(inv.getPaidAmount()))
                        .append(", balance=").append(money(inv.getBalanceAmount()))
                        .append(", paymentStatus=").append(inv.getPaymentStatus())
                        .append("\n");
            }
            if (invoices.size() > shown) {
                sb.append("...and ").append(invoices.size() - shown).append(" more. Ask me to show more if needed.\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to fetch customer invoices: " + safeMessage(ex);
        }
    }

    @Tool(name = "get_invoice_detail", description = "Get full details of a single invoice: line items (product, qty, price, line total), subtotal, discount, taxes, grand total, paid amount, balance and payment status. Use this when the user asks for details of a specific invoice.")
    public String getInvoiceDetail(
            @ToolParam(description = "Invoice id as returned by get_customer_invoices.", required = true) Long invoiceId) {
        if (invoiceId == null) {
            return "Invoice id is required. Use get_customer_invoices first to find the invoice id.";
        }
        try {
            InvoiceResponse inv = invoiceService.get("x", invoiceId);
            StringBuilder sb = new StringBuilder();
            sb.append("Invoice details:\n");
            sb.append("- Invoice no: ").append(inv.getInvoiceNo()).append("\n");
            sb.append("- Date: ").append(inv.getInvoiceDate()).append("\n");
            sb.append("- Customer: ").append(inv.getCustomerName()).append("\n");
            if (inv.getItems() != null) {
                sb.append("Items:\n");
                for (InvoiceItemResponse item : inv.getItems()) {
                    sb.append("  - ").append(item.getProductName())
                            .append(", qty=").append(item.getQty())
                            .append(", price=").append(money(item.getPrice()))
                            .append(", lineTotal=").append(money(item.getLineTotal()))
                            .append("\n");
                }
            }
            sb.append("- Subtotal: ").append(money(inv.getSubtotal())).append("\n");
            if (inv.getDiscountAmount() != null && inv.getDiscountAmount().signum() != 0) {
                sb.append("- Discount: ").append(money(inv.getDiscountAmount())).append("\n");
            }
            if (inv.getTaxAmount() != null && inv.getTaxAmount().signum() != 0) {
                sb.append("- Tax: ").append(money(inv.getTaxAmount())).append("\n");
            }
            sb.append("- Grand total: ").append(money(inv.getGrandTotal())).append("\n");
            sb.append("- Paid: ").append(money(inv.getPaidAmount())).append("\n");
            sb.append("- Balance: ").append(money(inv.getBalanceAmount())).append("\n");
            sb.append("- Payment status: ").append(inv.getPaymentStatus());
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to fetch invoice detail: " + safeMessage(ex);
        }
    }

    @Tool(name = "get_business_growth_report", description = "Get a comprehensive business growth report for the last 30 days (or a custom period): sales, collection, expenses, net revenue/profit, outstanding balance, new customers, total invoices, top selling products, customers with dues and low-stock products. Use this whenever the user asks for an overall business health, growth, performance, profit, loss, or report covering sales + collection + outstanding + customers together.")
    public String getBusinessGrowthReport(
            @ToolParam(description = "Start date in YYYY-MM-DD format. Optional, defaults to 30 days ago.", required = false) String startDate,
            @ToolParam(description = "End date in YYYY-MM-DD format. Optional, defaults to today.", required = false) String endDate) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate safeStart = parseDate(startDate, today.minusDays(29));
            LocalDate safeEnd = parseDate(endDate, today);
            if (safeStart.isAfter(safeEnd)) {
                return "Start date cannot be after end date.";
            }
            OwnerAnalyticsResponse report = analyticsService.ownerOverview("x", safeStart, safeEnd);
            StringBuilder sb = new StringBuilder();
            sb.append("Business growth report (").append(safeStart).append(" to ").append(safeEnd).append("):\n");
            sb.append("- Sales: ").append(money(report.getTotalSales())).append("\n");
            sb.append("- Collection: ").append(money(report.getTotalCollection())).append("\n");
            sb.append("- Expenses: ").append(money(report.getTotalExpense())).append("\n");
            sb.append("- Net revenue (profit): ").append(money(report.getNetRevenue())).append("\n");
            sb.append("- Outstanding balance: ").append(money(report.getOutstandingAmount())).append("\n");
            sb.append("- New customers: ").append(report.getNewCustomers()).append("\n");
            sb.append("- Invoices created: ").append(report.getTotalInvoices()).append("\n");
            sb.append("\nSales by period (last points):\n");
            appendTrendTail(sb, report.getSalesTrend(), 3);
            sb.append("\nCollection by period (last points):\n");
            appendTrendTail(sb, report.getCollectionTrend(), 3);
            sb.append("\nOutstanding by period (last points):\n");
            appendTrendTail(sb, report.getOutstandingTrend(), 3);
            if (report.getNetProfitTrend() != null && !report.getNetProfitTrend().isEmpty()) {
                sb.append("\nNet profit by period (last points):\n");
                appendTrendTail(sb, report.getNetProfitTrend(), 3);
            }
            if (report.getExpenseByCategory() != null && !report.getExpenseByCategory().isEmpty()) {
                sb.append("\nExpenses by category:\n");
                for (MetricPointResponse point : report.getExpenseByCategory()) {
                    sb.append("- ").append(point.getLabel()).append(": ").append(money(point.getValue())).append("\n");
                }
            }
            sb.append("\nTop selling products (this period):\n");
            PageResponse<TopSellingProductResponse> topProducts = analyticsService.topSellingProducts("x", safeStart, safeEnd, null, 0, 5);
            if (topProducts.getRecords().isEmpty()) {
                sb.append("- None\n");
            } else {
                for (TopSellingProductResponse product : topProducts.getRecords()) {
                    sb.append("- ").append(product.getProductName())
                            .append(", qty sold=").append(product.getTotalQtySold())
                            .append(", sales=").append(money(product.getTotalSalesAmount()))
                            .append(", stock left=").append(product.getCurrentStockQty())
                            .append("\n");
                }
            }
            sb.append("\nCustomers with dues:\n");
            PageResponse<CustomerDueResponse> dues = analyticsService.customerDueList("x", null, 0, 5);
            if (dues.getRecords().isEmpty()) {
                sb.append("- None\n");
            } else {
                for (CustomerDueResponse due : dues.getRecords()) {
                    sb.append("- ").append(due.getCustomerName())
                            .append(", balance=").append(money(due.getCurrentBalance()))
                            .append("\n");
                }
            }
            sb.append("\nLow-stock products:\n");
            PageResponse<LowStockProductResponse> lowStock = analyticsService.lowStockProducts("x", 0, 5);
            if (lowStock.getRecords().isEmpty()) {
                sb.append("- None\n");
            } else {
                for (LowStockProductResponse product : lowStock.getRecords()) {
                    sb.append("- ").append(product.getProductName())
                            .append(", stock=").append(product.getStockQty())
                            .append(", min=").append(product.getMinStockQty())
                            .append("\n");
                }
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to fetch business growth report: " + safeMessage(ex);
        }
    }

    @Tool(name = "get_top_products", description = "Get the best selling products for a period. Returns product name, quantity sold, sales amount and current stock, sorted by sales. Use this when the user asks about top/best selling products or what sells the most.")
    public String getTopProducts(
            @ToolParam(description = "Start date in YYYY-MM-DD format. Optional, defaults to 30 days ago.", required = false) String startDate,
            @ToolParam(description = "End date in YYYY-MM-DD format. Optional, defaults to today.", required = false) String endDate,
            @ToolParam(description = "How many products to show, between 1 and 10. Defaults to 5.", required = false) Integer limit) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate safeStart = parseDate(startDate, today.minusDays(29));
            LocalDate safeEnd = parseDate(endDate, today);
            if (safeStart.isAfter(safeEnd)) {
                return "Start date cannot be after end date.";
            }
            int safeLimit = limit == null ? 5 : Math.max(1, Math.min(limit, 10));
            PageResponse<TopSellingProductResponse> topProducts = analyticsService.topSellingProducts("x", safeStart, safeEnd, null, 0, safeLimit);
            if (topProducts.getRecords().isEmpty()) {
                return "No product sales found for this period.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Top selling products (").append(safeStart).append(" to ").append(safeEnd).append("):\n");
            for (TopSellingProductResponse product : topProducts.getRecords()) {
                sb.append("- ").append(product.getProductName())
                        .append(", qty sold=").append(product.getTotalQtySold())
                        .append(", sales=").append(money(product.getTotalSalesAmount()))
                        .append(", stock left=").append(product.getCurrentStockQty())
                        .append("\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to fetch top products: " + safeMessage(ex);
        }
    }

    @Tool(name = "get_customer_dues", description = "List customers who have an outstanding (unpaid) balance, largest first. Returns customer name, mobile and current balance. Use this when the user asks who owes money, pending payments, or which customers have dues.")
    public String getCustomerDues(
            @ToolParam(description = "How many customers to show, between 1 and 20. Defaults to 10.", required = false) Integer limit) {
        try {
            int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 20));
            PageResponse<CustomerDueResponse> dues = analyticsService.customerDueList("x", null, 0, safeLimit);
            if (dues.getRecords().isEmpty()) {
                return "No customers have outstanding dues.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Customers with dues (").append(dues.getTotalRecords()).append(" total):\n");
            for (CustomerDueResponse due : dues.getRecords()) {
                sb.append("- id=").append(due.getCustomerId())
                        .append(", name=").append(due.getCustomerName())
                        .append(", mobile=").append(due.getMobile())
                        .append(", balance=").append(money(due.getCurrentBalance()))
                        .append("\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to fetch customer dues: " + safeMessage(ex);
        }
    }

    @Tool(name = "list_customers", description = "List customers of this business, newest first. Returns customer id, name, mobile and outstanding balance. Use this when the user asks to see all customers or a list of customers. Optionally filter by name/mobile/email or only show customers with dues.")
    public String listCustomers(
            @ToolParam(description = "Optional filter text: customer name, 10-digit mobile or email. Leave empty to list all.", required = false) String search,
            @ToolParam(description = "Set to true to only show customers who have an outstanding balance (dues).", required = false) Boolean onlyWithDues,
            @ToolParam(description = "How many customers to show, between 1 and 50. Defaults to 10.", required = false) Integer limit) {
        try {
            Company company = accessControlService.getCurrentCompany();
            int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
            List<Customer> customers;
            if (Boolean.TRUE.equals(onlyWithDues)) {
                customers = customerRepository.findByCompanyAndActiveTrueAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(company, BigDecimal.ZERO);
            } else {
                customers = customerRepository.findAllByCompanyWithFilters(company, true, blankToNull(search));
            }
            if (customers.isEmpty()) {
                return "No customers found.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Customers:\n");
            int shown = Math.min(customers.size(), safeLimit);
            for (int i = 0; i < shown; i++) {
                Customer c = customers.get(i);
                sb.append("- id=").append(c.getId())
                        .append(", name=").append(c.getName())
                        .append(", mobile=").append(c.getMobile())
                        .append(", outstanding=").append(money(c.getCurrentBalance()))
                        .append("\n");
            }
            if (customers.size() > shown) {
                sb.append("...and ").append(customers.size() - shown).append(" more. Ask me to show more if needed.");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "Unable to list customers: " + safeMessage(ex);
        }
    }

    private void appendTrendTail(StringBuilder sb, List<MetricPointResponse> trend, int count) {
        if (trend == null || trend.isEmpty()) {
            sb.append("- No data\n");
            return;
        }
        int from = Math.max(0, trend.size() - count);
        for (int i = from; i < trend.size(); i++) {
            MetricPointResponse point = trend.get(i);
            sb.append("- ").append(point.getLabel()).append(": ").append(money(point.getValue())).append("\n");
        }
    }

    private Map<Integer, BigDecimal> collectionByDay(int year, int month) {
        Map<Integer, BigDecimal> byDay = new LinkedHashMap<>();
        Company company = accessControlService.getCurrentCompany();
        YearMonth selected = YearMonth.of(year, month);
        List<Payment> payments = paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company);
        for (Payment payment : payments) {
            if (payment.getPaymentDate() != null && YearMonth.from(payment.getPaymentDate()).equals(selected)) {
                int day = payment.getPaymentDate().getDayOfMonth();
                byDay.merge(day, payment.getAmount(), BigDecimal::add);
            }
        }
        return byDay;
    }

    private LocalDate parseDate(String date, LocalDate fallback) {
        if (date == null || date.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    @Getter
    @Setter
    public static class InvoiceItemInput {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Product id from find_product.")
        private Long productId;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Quantity, minimum 1.")
        private Integer qty;

        @JsonPropertyDescription("Unit price. Optional, leave empty to use the product's price.")
        private BigDecimal price;
    }
}