package com.billing.ai.service;

import com.billing.ai.audit.AiAuditService;
import com.billing.ai.context.AiSecurityContext;
import com.billing.ai.context.AiUserContext;
import com.billing.ai.dto.AiChart;
import com.billing.ai.dto.AiChartSeries;
import com.billing.ai.dto.AiCancelRequest;
import com.billing.ai.dto.AiChatRequest;
import com.billing.ai.dto.AiChatResponse;
import com.billing.ai.dto.AiConfirmRequest;
import com.billing.ai.dto.AiDraftAction;
import com.billing.ai.dto.AiDraftTokenPayload;
import com.billing.ai.parser.AiIntent;
import com.billing.ai.parser.AiIntentParser;
import com.billing.ai.parser.AiOperation;
import com.billing.ai.security.AiDraftTokenService;
import com.billing.ai.security.AiPermissionValidator;
import com.billing.ai.security.AiRateLimiter;
import com.billing.dto.PageResponse;
import com.billing.dto.analytics.LowStockProductResponse;
import com.billing.dto.analytics.MetricPointResponse;
import com.billing.dto.analytics.SalesByCategoryResponse;
import com.billing.dto.customer.CustomerRequest;
import com.billing.dto.customer.CustomerResponse;
import com.billing.dto.dashboard.DashboardSummaryResponse;
import com.billing.dto.expense.ProfitLossPointResponse;
import com.billing.dto.expense.ProfitLossReportResponse;
import com.billing.dto.invoice.InvoiceItemRequest;
import com.billing.dto.invoice.InvoiceRequest;
import com.billing.dto.invoice.InvoiceResponse;
import com.billing.dto.payment.PaymentRequest;
import com.billing.dto.payment.PaymentResponse;
import com.billing.dto.product.ProductRequest;
import com.billing.dto.product.ProductResponse;
import com.billing.dto.productcategory.ProductCategoryResponse;
import com.billing.dto.productsubcategory.ProductSubCategoryResponse;
import com.billing.exception.BadRequestException;
import com.billing.exception.ChatbotDisabledException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.service.AnalyticsService;
import com.billing.service.AuditLogService;
import com.billing.service.CustomerService;
import com.billing.service.DashboardService;
import com.billing.service.ExpenseService;
import com.billing.service.InvoiceService;
import com.billing.service.PaymentService;
import com.billing.service.ProductCategoryService;
import com.billing.service.ProductService;
import com.billing.service.ProductSubCategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiSecurityContext aiSecurityContext;
    private final AiRateLimiter aiRateLimiter;
    private final AiIntentParser aiIntentParser;
    private final AiPermissionValidator aiPermissionValidator;
    private final AiDraftTokenService aiDraftTokenService;
    private final AiAuditService aiAuditService;
    private final CustomerService customerService;
    private final ProductService productService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final DashboardService dashboardService;
    private final AnalyticsService analyticsService;
    private final ExpenseService expenseService;
    private final ProductCategoryService productCategoryService;
    private final ProductSubCategoryService productSubCategoryService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Transactional
    public AiChatResponse chat(Authentication authentication, AiChatRequest request) {
        AiUserContext context = aiSecurityContext.current(authentication, false);
        String prompt = normalizePrompt(request.getMessage());
        if (!context.isChatbotEnabled()) {
            aiAuditService.log(context, prompt, AiOperation.UNKNOWN.name(), AiAuditService.AI_CHAT, "DENIED_DISABLED");
            throw new ChatbotDisabledException("Company Disabled Chatbot");
        }
        if (!hasAssistantAccess(context)) {
            aiAuditService.log(context, prompt, AiOperation.UNKNOWN.name(), AiAuditService.AI_CHAT, "DENIED_ASSISTANT_ACCESS");
            return message("You do not have permission to use AI assistant.", AiOperation.UNKNOWN);
        }

        aiRateLimiter.check(context);
        AiIntent intent = aiIntentParser.parse(prompt);
        AiOperation operation = intent.getOperation() == null ? AiOperation.UNKNOWN : intent.getOperation();
        try {
            if (operation == AiOperation.UNKNOWN) {
                AiChatResponse response = message("I can help with customers, products, stock, invoices, payments, sales, expenses, inventory, and profit summaries. Please rephrase your request.", operation);
                aiAuditService.log(context, prompt, operation.name(), AiAuditService.AI_CHAT, "UNKNOWN");
                return response;
            }

            var denied = aiPermissionValidator.denialMessage(context, operation);
            if (denied.isPresent()) {
                AiChatResponse response = message(denied.get(), operation);
                aiAuditService.log(context, prompt, operation.name(), AiAuditService.AI_CHAT, "DENIED_PERMISSION");
                return response;
            }

            AiChatResponse response = operation.isWriteOperation()
                    ? createDraft(context, operation, intent.getSlots())
                    : executeRead(context, operation, intent.getSlots());
            aiAuditService.log(context, prompt, operation.name(), AiAuditService.AI_CHAT,
                    operation.isWriteOperation() ? "DRAFT_CREATED" : "SUCCESS");
            return response;
        } catch (BadRequestException | ResourceNotFoundException ex) {
            aiAuditService.log(context, prompt, operation.name(), AiAuditService.AI_CHAT, "FAILED");
            return message(ex.getMessage(), operation);
        } catch (RuntimeException ex) {
            aiAuditService.log(context, prompt, operation.name(), AiAuditService.AI_CHAT, "FAILED");
            return message("Unable to complete AI request. Please check the details and try again.", operation);
        }
    }

    @Transactional
    public AiChatResponse confirm(Authentication authentication, AiConfirmRequest request) {
        AiUserContext context = aiSecurityContext.current(authentication, true);
        if (!hasAssistantAccess(context)) {
            aiAuditService.log(context, "CONFIRM", AiOperation.UNKNOWN.name(), AiAuditService.AI_ACTION, "DENIED_ASSISTANT_ACCESS");
            return message("You do not have permission to use AI assistant.", AiOperation.UNKNOWN);
        }
        aiRateLimiter.check(context);
        AiDraftTokenPayload draft = aiDraftTokenService.parse(request.getDraftId());
        validateDraftContext(context, draft);
        AiOperation operation = draft.getOperation() == null ? AiOperation.UNKNOWN : draft.getOperation();
        if (!operation.isWriteOperation()) {
            throw new BadRequestException("Invalid AI draft action");
        }
        var denied = aiPermissionValidator.denialMessage(context, operation);
        if (denied.isPresent()) {
            aiAuditService.log(context, "CONFIRM", operation.name(), AiAuditService.AI_ACTION, "DENIED_PERMISSION");
            return message(denied.get(), operation);
        }
        try {
            Object result = executeWriteWithChatbotAudit(context, operation, draft.getPayload());
            aiAuditService.log(context, "CONFIRM", operation.name(), AiAuditService.AI_ACTION, "SUCCESS");
            return AiChatResponse.builder()
                    .message(successMessage(operation, result))
                    .intent(operation.name())
                    .action(AiAuditService.AI_ACTION)
                    .data(result)
                    .build();
        } catch (BadRequestException | ResourceNotFoundException ex) {
            aiAuditService.log(context, "CONFIRM", operation.name(), AiAuditService.AI_ACTION, "FAILED");
            return message(ex.getMessage(), operation);
        } catch (RuntimeException ex) {
            aiAuditService.log(context, "CONFIRM", operation.name(), AiAuditService.AI_ACTION, "FAILED");
            return message("Unable to complete AI action. Please check the draft and try again.", operation);
        }
    }

    @Transactional
    public AiChatResponse cancel(Authentication authentication, AiCancelRequest request) {
        AiUserContext context = aiSecurityContext.current(authentication, true);
        if (!hasAssistantAccess(context)) {
            aiAuditService.log(context, "CANCEL", AiOperation.UNKNOWN.name(), AiAuditService.AI_ACTION, "DENIED_ASSISTANT_ACCESS");
            return message("You do not have permission to use AI assistant.", AiOperation.UNKNOWN);
        }
        AiDraftTokenPayload draft = aiDraftTokenService.parse(request.getDraftId());
        validateDraftContext(context, draft);
        AiOperation operation = draft.getOperation() == null ? AiOperation.UNKNOWN : draft.getOperation();
        aiAuditService.log(context, "CANCEL", operation.name(), AiAuditService.AI_ACTION, "CANCELLED");
        return AiChatResponse.builder()
                .message("Draft cancelled.")
                .intent(operation.name())
                .action(AiAuditService.AI_ACTION)
                .build();
    }

    private AiChatResponse executeRead(AiUserContext context, AiOperation operation, Map<String, Object> slots) {
        String email = context.getEmail();
        String search = text(slots, "search");
        boolean chartRequested = text(slots, "chartType") != null;
        return switch (operation) {
            case CUSTOMER_SEARCH -> customerSearch(email, search);
            case PRODUCT_SEARCH, CURRENT_STOCK -> productSearch(email, search, operation == AiOperation.CURRENT_STOCK, chartRequested);
            case OUTSTANDING_CUSTOMERS -> outstandingCustomers(email);
            case INVOICE_SEARCH -> invoiceSearch(email, search);
            case PAYMENT_SEARCH -> paymentSearch(email, search);
            case SALES_SUMMARY -> salesSummary(email, slots);
            case COLLECTION_SUMMARY -> collectionSummary(email, slots);
            case EXPENSE_SUMMARY -> expenseSummary(email, slots);
            case INVENTORY_SUMMARY -> inventorySummary(email, chartRequested);
            case PROFIT_SUMMARY -> profitSummary(email, slots);
            default -> message("This operation is not supported yet.", operation);
        };
    }

    private AiChatResponse customerSearch(String email, String search) {
        PageResponse<CustomerResponse> customers = customerService.page(email, search, true, 0, 10);
        String text = customers.getRecords().isEmpty()
                ? "Customer Not Found"
                : "Found " + customers.getRecords().size() + " customer(s):\n" + customers.getRecords().stream()
                        .map(customer -> "- " + customer.getName() + " | Mobile: " + customer.getMobile() + " | Outstanding: " + money(customer.getCurrentBalance()))
                        .collect(Collectors.joining("\n"));
        return response(text, AiOperation.CUSTOMER_SEARCH, customers, null);
    }

    private AiChatResponse productSearch(String email, String search, boolean stockOnly, boolean chartRequested) {
        PageResponse<ProductResponse> products = productService.page(email, null, null, search, true, 0, 10);
        String text = products.getRecords().isEmpty()
                ? "Product Not Found"
                : products.getRecords().stream()
                        .map(product -> "- " + product.getName() + " | SKU: " + product.getSku() + " | Stock: " + product.getStockQty() + " | Rate: " + money(product.getSellingPrice()))
                        .collect(Collectors.joining("\n"));
        AiOperation operation = stockOnly ? AiOperation.CURRENT_STOCK : AiOperation.PRODUCT_SEARCH;
        return response(stockOnly ? "Current stock:\n" + text : "Found product(s):\n" + text, operation, products,
                chartRequested ? stockChart(operation, products) : null);
    }

    private AiChatResponse outstandingCustomers(String email) {
        List<CustomerResponse> customers = customerService.outstanding(email);
        String text = customers.isEmpty()
                ? "No outstanding customers found."
                : "Outstanding customers:\n" + customers.stream()
                        .limit(15)
                        .map(customer -> "- " + customer.getName() + " | Mobile: " + customer.getMobile() + " | Outstanding: " + money(customer.getCurrentBalance()))
                        .collect(Collectors.joining("\n"));
        return response(text, AiOperation.OUTSTANDING_CUSTOMERS, customers, null);
    }

    private AiChatResponse invoiceSearch(String email, String search) {
        PageResponse<InvoiceResponse> invoices = invoiceService.page(email, null, search, null, null, null, null, null, null, null, null, null, "ACTIVE", 0, 10);
        String text = invoices.getRecords().isEmpty()
                ? "Invoice Not Found"
                : "Found invoice(s):\n" + invoices.getRecords().stream()
                        .map(invoice -> "- " + invoice.getInvoiceNo() + " | " + invoice.getCustomerName() + " | Total: " + money(invoice.getTotalAmount()) + " | Balance: " + money(invoice.getBalanceAmount()))
                        .collect(Collectors.joining("\n"));
        return response(text, AiOperation.INVOICE_SEARCH, invoices, null);
    }

    private AiChatResponse paymentSearch(String email, String search) {
        PageResponse<PaymentResponse> payments = paymentService.page(email, search, null, null, null, null, null, null, null, null, "ACTIVE", 0, 10);
        String text = payments.getRecords().isEmpty()
                ? "Payment Not Found"
                : "Found payment(s):\n" + payments.getRecords().stream()
                        .map(payment -> "- " + payment.getCustomerName() + " | " + money(payment.getAmount()) + " | " + payment.getMode() + " | " + payment.getPaymentDate())
                        .collect(Collectors.joining("\n"));
        return response(text, AiOperation.PAYMENT_SEARCH, payments, null);
    }

    private AiChatResponse salesSummary(String email, Map<String, Object> slots) {
        DateRange range = dateRange(slots);
        DashboardSummaryResponse summary = dashboardService.summary(email, range.startDate(), range.endDate());
        String text = "Sales summary" + range.label() + ":\n"
                + "- Total sales: " + money(summary.getTotalSales()) + "\n"
                + "- Total invoices: " + summary.getTotalInvoices() + "\n"
                + "- Total collection: " + money(summary.getTotalCollection()) + "\n"
                + "- Outstanding: " + money(summary.getOutstandingAmount());
        return response(text, AiOperation.SALES_SUMMARY, summary, buildChart(email, AiOperation.SALES_SUMMARY, range));
    }

    private AiChatResponse collectionSummary(String email, Map<String, Object> slots) {
        DateRange range = dateRange(slots);
        DashboardSummaryResponse summary = dashboardService.summary(email, range.startDate(), range.endDate());
        String text = "Collection summary" + range.label() + ":\n"
                + "- Total collection: " + money(summary.getTotalCollection()) + "\n"
                + "- Outstanding: " + money(summary.getOutstandingAmount());
        return response(text, AiOperation.COLLECTION_SUMMARY, summary, buildChart(email, AiOperation.COLLECTION_SUMMARY, range));
    }

    private AiChatResponse expenseSummary(String email, Map<String, Object> slots) {
        DateRange range = dateRange(slots);
        ProfitLossReportResponse summary = expenseService.profitLossReport(email, null, null, null, null, range.startDate(), range.endDate(), null);
        String text = "Expense summary" + range.label() + ":\n"
                + "- Total expense: " + money(summary.getExpense()) + "\n"
                + "- Revenue: " + money(summary.getRevenue()) + "\n"
                + "- Net profit: " + money(summary.getNetProfit());
        return response(text, AiOperation.EXPENSE_SUMMARY, summary, buildChart(email, AiOperation.EXPENSE_SUMMARY, range));
    }

    private AiChatResponse inventorySummary(String email, boolean chartRequested) {
        PageResponse<ProductResponse> products = productService.page(email, null, null, null, true, 0, 100);
        PageResponse<LowStockProductResponse> lowStock = analyticsService.lowStockProducts(email, 0, 100);
        int totalUnits = products.getRecords().stream().map(ProductResponse::getStockQty).filter(Objects::nonNull).reduce(0, Integer::sum);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("products", products);
        data.put("lowStock", lowStock);
        data.put("totalUnits", totalUnits);
        String text = "Inventory summary:\n"
                + "- Active products loaded: " + products.getRecords().size() + "\n"
                + "- Total stock units: " + totalUnits + "\n"
                + "- Low stock products: " + lowStock.getTotalRecords();
        return response(text, AiOperation.INVENTORY_SUMMARY, data, chartRequested ? inventoryChart(lowStock) : null);
    }

    private AiChatResponse profitSummary(String email, Map<String, Object> slots) {
        DateRange range = dateRange(slots);
        ProfitLossReportResponse summary = expenseService.profitLossReport(email, null, null, null, null, range.startDate(), range.endDate(), null);
        String text = "Profit summary" + range.label() + ":\n"
                + "- Revenue: " + money(summary.getRevenue()) + "\n"
                + "- Expense: " + money(summary.getExpense()) + "\n"
                + "- Net profit: " + money(summary.getNetProfit());
        return response(text, AiOperation.PROFIT_SUMMARY, summary, buildChart(email, AiOperation.PROFIT_SUMMARY, range));
    }

    private AiChatResponse createDraft(AiUserContext context, AiOperation operation, Map<String, Object> slots) {
        return switch (operation) {
            case CREATE_CUSTOMER -> customerDraft(context, slots);
            case CREATE_PRODUCT -> productDraft(context, slots);
            case CREATE_INVOICE -> invoiceDraft(context, slots);
            case RECORD_PAYMENT -> paymentDraft(context, slots);
            default -> message("This action cannot be drafted.", operation);
        };
    }

    private AiChatResponse customerDraft(AiUserContext context, Map<String, Object> slots) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", text(slots, "name"));
        payload.put("mobile", text(slots, "mobile"));
        payload.put("email", text(slots, "email"));
        payload.put("address", text(slots, "address"));
        payload.put("gstNo", text(slots, "gstNo"));
        payload.put("active", true);

        List<String> missing = missing(payload, "name", "mobile");
        return draftResponse(context, AiOperation.CREATE_CUSTOMER, "Customer Draft", payload, payload, missing,
                missing.isEmpty() ? "Customer draft is ready. Please confirm to create it." : "Customer draft needs more information.");
    }

    private AiChatResponse productDraft(AiUserContext context, Map<String, Object> slots) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String categoryName = text(slots, "categoryName");
        String subCategoryName = text(slots, "subCategoryName");
        ProductCategoryResponse category = categoryName == null ? null : resolveSingleCategory(context.getEmail(), categoryName);
        ProductSubCategoryResponse subCategory = category == null || subCategoryName == null ? null : resolveSingleSubCategory(context.getEmail(), category.getId(), subCategoryName);

        payload.put("name", text(slots, "name"));
        payload.put("categoryId", category == null ? null : category.getId());
        payload.put("subCategoryId", subCategory == null ? null : subCategory.getId());
        payload.put("brand", text(slots, "brand"));
        payload.put("sku", text(slots, "sku"));
        payload.put("hsnCode", text(slots, "hsnCode"));
        payload.put("purchasePrice", decimal(slots, "purchasePrice"));
        payload.put("sellingPrice", decimal(slots, "sellingPrice"));
        payload.put("stockQty", integer(slots, "stockQty", 0));
        payload.put("minStockQty", integer(slots, "minStockQty", 0));
        payload.put("taxPercent", decimal(slots, "taxPercent", BigDecimal.ZERO));
        payload.put("active", true);

        Map<String, Object> fields = new LinkedHashMap<>(payload);
        fields.put("category", categoryName);
        fields.put("subCategory", subCategoryName);

        List<String> missing = missing(payload, "name", "categoryId", "subCategoryId", "sku", "purchasePrice", "sellingPrice", "taxPercent");
        return draftResponse(context, AiOperation.CREATE_PRODUCT, "Product Draft", fields, payload, missing,
                missing.isEmpty() ? "Product draft is ready. Please confirm to create it." : "Product draft needs more information.");
    }

    private AiChatResponse invoiceDraft(AiUserContext context, Map<String, Object> slots) {
        String customerName = text(slots, "customerName");
        String productName = text(slots, "productName");
        Integer quantity = integer(slots, "quantity", null);
        List<String> missing = new ArrayList<>();
        if (customerName == null) {
            missing.add("customerName");
        }
        if (productName == null) {
            missing.add("productName");
        }
        if (quantity == null || quantity <= 0) {
            missing.add("quantity");
        }
        CustomerResponse customer = customerName == null ? null : resolveSingleCustomer(context.getEmail(), customerName);
        ProductResponse product = productName == null ? null : resolveSingleProduct(context.getEmail(), productName);
        if (customer == null && customerName != null) {
            throw new ResourceNotFoundException("Customer Not Found");
        }
        if (product == null && productName != null) {
            throw new ResourceNotFoundException("Product Not Found");
        }
        if (customer != null && product != null && quantity != null && product.getStockQty() < quantity) {
            throw new BadRequestException("Insufficient Stock. Current stock of " + product.getName() + " is " + product.getStockQty() + ".");
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", product == null ? null : product.getId());
        item.put("qty", quantity);
        item.put("price", product == null ? null : product.getSellingPrice());
        item.put("taxPercent", product == null ? BigDecimal.ZERO : product.getTaxPercent());
        item.put("discountPercent", BigDecimal.ZERO);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customer == null ? null : customer.getId());
        payload.put("invoiceDate", LocalDate.now().toString());
        payload.put("discountAmount", BigDecimal.ZERO);
        payload.put("paidAmount", BigDecimal.ZERO);
        payload.put("items", List.of(item));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("customer", customer == null ? customerName : customer.getName());
        fields.put("product", product == null ? productName : product.getName());
        fields.put("qty", quantity);
        fields.put("rate", product == null ? null : money(product.getSellingPrice()));
        fields.put("amount", product == null || quantity == null ? null : money(product.getSellingPrice().multiply(BigDecimal.valueOf(quantity))));

        if (payload.get("customerId") == null) {
            missing.add("customerId");
        }
        if (item.get("productId") == null) {
            missing.add("productId");
        }
        return draftResponse(context, AiOperation.CREATE_INVOICE, "Invoice Draft", fields, payload, missing,
                missing.isEmpty() ? "Invoice draft is ready. Please confirm to create it." : "Invoice draft needs more information.");
    }

    private AiChatResponse paymentDraft(AiUserContext context, Map<String, Object> slots) {
        String customerName = text(slots, "customerName");
        BigDecimal amount = decimal(slots, "amount");
        List<String> missing = new ArrayList<>();
        if (customerName == null) {
            missing.add("customerName");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            missing.add("amount");
        }
        CustomerResponse customer = customerName == null ? null : resolveSingleCustomer(context.getEmail(), customerName);
        if (customer == null && customerName != null) {
            throw new ResourceNotFoundException("Customer Not Found");
        }
        if (customer != null && amount != null && amount.compareTo(customer.getCurrentBalance()) > 0) {
            throw new BadRequestException("Payment exceeds customer outstanding balance.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customer == null ? null : customer.getId());
        payload.put("amount", amount);
        payload.put("paymentDate", LocalDate.now().toString());
        payload.put("mode", text(slots, "paymentMode", "Cash"));
        payload.put("remarks", "Recorded by AI Assistant");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("customer", customer == null ? customerName : customer.getName());
        fields.put("amount", amount == null ? null : money(amount));
        fields.put("mode", payload.get("mode"));
        fields.put("paymentDate", payload.get("paymentDate"));

        if (payload.get("customerId") == null) {
            missing.add("customerId");
        }
        return draftResponse(context, AiOperation.RECORD_PAYMENT, "Payment Draft", fields, payload, missing,
                missing.isEmpty() ? "Payment draft is ready. Please confirm to record it." : "Payment draft needs more information.");
    }

    private Object executeWrite(AiUserContext context, AiOperation operation, Map<String, Object> payload) {
        String email = context.getEmail();
        return switch (operation) {
            case CREATE_CUSTOMER -> {
                CustomerRequest request = objectMapper.convertValue(payload, CustomerRequest.class);
                validateBean(request);
                yield customerService.create(email, request);
            }
            case CREATE_PRODUCT -> {
                ProductRequest request = objectMapper.convertValue(payload, ProductRequest.class);
                validateBean(request);
                yield productService.create(email, request);
            }
            case CREATE_INVOICE -> {
                InvoiceRequest request = invoiceRequest(payload);
                validateBean(request);
                yield invoiceService.create(email, request);
            }
            case RECORD_PAYMENT -> {
                PaymentRequest request = paymentRequest(payload);
                validateBean(request);
                yield paymentService.create(email, request);
            }
            default -> throw new BadRequestException("Unsupported AI action");
        };
    }

    private Object executeWriteWithChatbotAudit(AiUserContext context, AiOperation operation, Map<String, Object> payload) {
        HttpServletRequest request = currentRequest();
        Object previousSuffix = request == null ? null : request.getAttribute(AuditLogService.AUDIT_ACTOR_SUFFIX_REQUEST_ATTRIBUTE);
        try {
            if (request != null) {
                request.setAttribute(AuditLogService.AUDIT_ACTOR_SUFFIX_REQUEST_ATTRIBUTE, AiAuditService.CHATBOT_ACTOR_SUFFIX);
            }
            return executeWrite(context, operation, payload);
        } finally {
            if (request != null) {
                if (previousSuffix == null) {
                    request.removeAttribute(AuditLogService.AUDIT_ACTOR_SUFFIX_REQUEST_ATTRIBUTE);
                } else {
                    request.setAttribute(AuditLogService.AUDIT_ACTOR_SUFFIX_REQUEST_ATTRIBUTE, previousSuffix);
                }
            }
        }
    }

    private InvoiceRequest invoiceRequest(Map<String, Object> payload) {
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId(longValue(payload.get("customerId")));
        request.setInvoiceDate(localDate(payload.get("invoiceDate")));
        request.setDiscountAmount(decimalValue(payload.get("discountAmount"), BigDecimal.ZERO));
        request.setPaidAmount(decimalValue(payload.get("paidAmount"), BigDecimal.ZERO));
        request.setPaymentMode(textValue(payload.get("paymentMode")));
        Object itemsValue = payload.get("items");
        List<InvoiceItemRequest> items = new ArrayList<>();
        if (itemsValue instanceof List<?> rows) {
            for (Object row : rows) {
                Map<?, ?> map = objectMapper.convertValue(row, Map.class);
                InvoiceItemRequest item = new InvoiceItemRequest();
                item.setProductId(longValue(map.get("productId")));
                item.setQty(integerValue(map.get("qty")));
                item.setPrice(decimalValue(map.get("price"), null));
                item.setTaxPercent(decimalValue(map.get("taxPercent"), null));
                item.setDiscountPercent(decimalValue(map.get("discountPercent"), BigDecimal.ZERO));
                items.add(item);
            }
        }
        request.setItems(items);
        return request;
    }

    private PaymentRequest paymentRequest(Map<String, Object> payload) {
        PaymentRequest request = new PaymentRequest();
        request.setCustomerId(longValue(payload.get("customerId")));
        request.setInvoiceId(longValue(payload.get("invoiceId")));
        request.setAmount(decimalValue(payload.get("amount"), null));
        request.setPaymentDate(localDate(payload.get("paymentDate")));
        request.setMode(textValue(payload.get("mode")));
        request.setRemarks(textValue(payload.get("remarks")));
        return request;
    }

    private void validateBean(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BadRequestException(violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse("Request data is invalid."));
        }
    }

    private AiChatResponse draftResponse(AiUserContext context,
                                         AiOperation operation,
                                         String title,
                                         Map<String, Object> fields,
                                         Map<String, Object> payload,
                                         List<String> missing,
                                         String message) {
        boolean confirmable = missing.isEmpty();
        AiDraftTokenService.DraftToken draftToken = confirmable ? aiDraftTokenService.create(context, operation, payload) : null;
        AiDraftAction draft = AiDraftAction.builder()
                .draftId(draftToken == null ? null : draftToken.token())
                .operation(operation.name())
                .title(title)
                .fields(fields)
                .missingFields(missing)
                .confirmable(confirmable)
                .expiresAt(draftToken == null ? null : draftToken.expiresAt())
                .build();
        return AiChatResponse.builder()
                .message(message)
                .intent(operation.name())
                .action(AiAuditService.AI_CHAT)
                .requiresConfirmation(confirmable)
                .draft(draft)
                .build();
    }

    private void validateDraftContext(AiUserContext context, AiDraftTokenPayload draft) {
        if (!Objects.equals(context.getCompanyId(), draft.getCompanyId()) || !Objects.equals(context.getUserId(), draft.getUserId())) {
            throw new AccessDeniedException("Invalid AI draft context");
        }
    }

    private CustomerResponse resolveSingleCustomer(String email, String name) {
        List<CustomerResponse> records = customerService.page(email, name, true, 0, 5).getRecords();
        if (records.isEmpty()) {
            return null;
        }
        return exactOrSingle(records, name, CustomerResponse::getName, "Multiple customers matched. Please be more specific.");
    }

    private ProductResponse resolveSingleProduct(String email, String name) {
        List<ProductResponse> records = productService.page(email, null, null, name, true, 0, 5).getRecords();
        if (records.isEmpty()) {
            return null;
        }
        return exactOrSingle(records, name, ProductResponse::getName, "Multiple products matched. Please be more specific.");
    }

    private ProductCategoryResponse resolveSingleCategory(String email, String name) {
        List<ProductCategoryResponse> records = productCategoryService.list(email, name, true);
        if (records.isEmpty()) {
            return null;
        }
        return exactOrSingle(records, name, ProductCategoryResponse::getCategoryName, "Multiple product categories matched. Please be more specific.");
    }

    private ProductSubCategoryResponse resolveSingleSubCategory(String email, Long categoryId, String name) {
        List<ProductSubCategoryResponse> records = productSubCategoryService.list(email, categoryId, name, true);
        if (records.isEmpty()) {
            return null;
        }
        return exactOrSingle(records, name, ProductSubCategoryResponse::getSubCategoryName, "Multiple product sub categories matched. Please be more specific.");
    }

    private <T> T exactOrSingle(List<T> records, String search, java.util.function.Function<T, String> nameResolver, String ambiguousMessage) {
        String normalized = search == null ? "" : search.trim();
        List<T> exact = records.stream()
                .filter(record -> nameResolver.apply(record) != null && nameResolver.apply(record).equalsIgnoreCase(normalized))
                .toList();
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (records.size() == 1) {
            return records.get(0);
        }
        throw new BadRequestException(ambiguousMessage);
    }

    private List<String> missing(Map<String, Object> payload, String... keys) {
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            Object value = payload.get(key);
            if (value == null || (value instanceof String string && string.isBlank())) {
                missing.add(key);
            }
        }
        return missing;
    }

    private DateRange dateRange(Map<String, Object> slots) {
        String dateRange = text(slots, "dateRange");
        String chartType = text(slots, "chartType");
        LocalDate today = LocalDate.now();
        if ("TODAY".equalsIgnoreCase(dateRange)) {
            return new DateRange(today, today, " for today", chartType);
        }
        if ("THIS_MONTH".equalsIgnoreCase(dateRange) || "MONTHLY".equalsIgnoreCase(dateRange)) {
            YearMonth month = YearMonth.from(today);
            return new DateRange(month.atDay(1), month.atEndOfMonth(), " for " + month, chartType);
        }
        return new DateRange(null, null, "", chartType);
    }

    private AiChatResponse response(String message, AiOperation operation, Object data, AiChart chart) {
        return AiChatResponse.builder()
                .message(message)
                .intent(operation.name())
                .action(AiAuditService.AI_CHAT)
                .chart(chart)
                .data(data)
                .build();
    }

    private AiChatResponse message(String message, AiOperation operation) {
        return response(message, operation, null, null);
    }

    private boolean hasAssistantAccess(AiUserContext context) {
        return context.hasPermission("AI_ASSISTANT", "VIEW");
    }

    private AiChart buildChart(String email, AiOperation operation, DateRange range) {
        if (range == null || range.chartType() == null) {
            return null;
        }
        String chartType = range.chartType();
        return switch (operation) {
            case SALES_SUMMARY -> "PIE".equals(chartType)
                    ? salesCategoryChart(email, range)
                    : trendChart(chartType, "Sales chart" + range.label(), analyticsService.ownerOverview(email, range.startDate(), range.endDate()).getSalesTrend(), "Sales");
            case COLLECTION_SUMMARY -> "PIE".equals(chartType)
                    ? trendChart("PIE", "Collection chart" + range.label(), analyticsService.ownerOverview(email, range.startDate(), range.endDate()).getCollectionTrend(), "Collection")
                    : trendChart(chartType, "Collection chart" + range.label(), analyticsService.ownerOverview(email, range.startDate(), range.endDate()).getCollectionTrend(), "Collection");
            case EXPENSE_SUMMARY -> "PIE".equals(chartType)
                    ? expenseCategoryChart("Expense chart" + range.label(), expenseService.profitLossReport(email, null, null, null, null, range.startDate(), range.endDate(), null).getExpenseByCategory())
                    : trendChart(chartType, "Expense chart" + range.label(), analyticsService.ownerOverview(email, range.startDate(), range.endDate()).getExpenseTrend(), "Expense");
            case PROFIT_SUMMARY -> "PIE".equals(chartType)
                    ? trendChart("PIE", "Profit chart" + range.label(), analyticsService.ownerOverview(email, range.startDate(), range.endDate()).getNetProfitTrend(), "Net Profit")
                    : trendChart(chartType, "Profit chart" + range.label(), analyticsService.ownerOverview(email, range.startDate(), range.endDate()).getNetProfitTrend(), "Net Profit");
            default -> null;
        };
    }

    private AiChart salesCategoryChart(String email, DateRange range) {
        List<SalesByCategoryResponse> rows = analyticsService.salesByCategory(email, range.startDate(), range.endDate(), 7);
        List<Map<String, Object>> data = rows.stream()
                .map(row -> chartRow(
                        "label", row.getCategoryName(),
                        "value", row.getTotalAmount(),
                        "percentage", row.getPercentage()))
                .toList();
        return chart("PIE", "Sales by category" + range.label(), data, "label", "value", "Sales");
    }

    private AiChart expenseCategoryChart(String title, List<ProfitLossPointResponse> rows) {
        List<Map<String, Object>> data = rows == null ? List.of() : rows.stream()
                .map(row -> chartRow(
                        "label", row.getLabel(),
                        "value", row.getValue() == null ? BigDecimal.ZERO : row.getValue()))
                .toList();
        return chart("PIE", title, data, "label", "value", "Expense");
    }

    private AiChart trendChart(String type, String title, List<MetricPointResponse> rows, String label) {
        List<Map<String, Object>> data = rows == null ? List.of() : rows.stream()
                .map(row -> chartRow(
                        "label", row.getLabel(),
                        "value", row.getValue() == null ? BigDecimal.ZERO : row.getValue()))
                .toList();
        return chart(type, title, data, "label", "value", label);
    }

    private AiChart inventoryChart(PageResponse<LowStockProductResponse> lowStock) {
        List<Map<String, Object>> data = lowStock.getRecords().stream()
                .limit(8)
                .map(product -> chartRow(
                        "label", product.getProductName(),
                        "value", product.getStockQty() == null ? 0 : product.getStockQty(),
                        "minStock", product.getMinStockQty() == null ? 0 : product.getMinStockQty()))
                .toList();
        return data.isEmpty() ? null : chart("BAR", "Low stock products", data, "label", "value", "Stock");
    }

    private AiChart stockChart(AiOperation operation, PageResponse<ProductResponse> products) {
        if (operation != AiOperation.CURRENT_STOCK || products.getRecords().isEmpty()) {
            return null;
        }
        List<Map<String, Object>> data = products.getRecords().stream()
                .limit(8)
                .map(product -> chartRow(
                        "label", product.getName(),
                        "value", product.getStockQty() == null ? 0 : product.getStockQty(),
                        "rate", product.getSellingPrice() == null ? BigDecimal.ZERO : product.getSellingPrice()))
                .toList();
        return chart("BAR", "Current stock", data, "label", "value", "Stock");
    }

    private AiChart chart(String type, String title, List<Map<String, Object>> data, String labelKey, String valueKey, String seriesLabel) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return AiChart.builder()
                .type(type)
                .title(title)
                .labelKey(labelKey)
                .valueKey(valueKey)
                .series(List.of(AiChartSeries.builder()
                        .key(valueKey)
                        .label(seriesLabel)
                        .color("#0EA5E9")
                        .build()))
                .data(data)
                .build();
    }

    private Map<String, Object> chartRow(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }

    private String successMessage(AiOperation operation, Object result) {
        return switch (operation) {
            case CREATE_CUSTOMER -> "Customer created successfully.";
            case CREATE_PRODUCT -> "Product created successfully.";
            case CREATE_INVOICE -> result instanceof InvoiceResponse invoice
                    ? "Invoice created successfully: " + invoice.getInvoiceNo()
                    : "Invoice created successfully.";
            case RECORD_PAYMENT -> "Payment recorded successfully.";
            default -> "Action completed successfully.";
        };
    }

    private String normalizePrompt(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(Map<String, Object> slots, String key) {
        return text(slots, key, null);
    }

    private String text(Map<String, Object> slots, String key, String defaultValue) {
        Object value = slots == null ? null : slots.get(key);
        String text = value == null ? defaultValue : String.valueOf(value).trim();
        return text == null || text.isBlank() ? null : text;
    }

    private String textValue(Object value) {
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isBlank() ? null : text;
    }

    private BigDecimal decimal(Map<String, Object> slots, String key) {
        return decimal(slots, key, null);
    }

    private BigDecimal decimal(Map<String, Object> slots, String key, BigDecimal defaultValue) {
        Object value = slots == null ? null : slots.get(key);
        return decimalValue(value, defaultValue);
    }

    private BigDecimal decimalValue(Object value, BigDecimal defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value).trim());
    }

    private Integer integer(Map<String, Object> slots, String key, Integer defaultValue) {
        Object value = slots == null ? null : slots.get(key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : integerValue(value);
    }

    private Integer integerValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value).trim());
    }

    private LocalDate localDate(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private String money(BigDecimal value) {
        return "INR " + (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private record DateRange(LocalDate startDate, LocalDate endDate, String label, String chartType) {
    }
}
