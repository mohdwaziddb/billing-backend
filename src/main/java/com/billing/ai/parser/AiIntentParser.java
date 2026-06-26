package com.billing.ai.parser;

import com.billing.ai.prompts.AiPromptBuilder;
import com.billing.ai.service.OllamaClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiIntentParser {

    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:rs\\.?|inr|\\p{Sc})?\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILE_PATTERN = Pattern.compile("\\b(\\d{10})\\b");

    private final OllamaClient ollamaClient;
    private final AiPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public AiIntent parse(String message) {
        AiIntent ollamaIntent = parseWithOllama(message);
        if (ollamaIntent.getOperation() != AiOperation.UNKNOWN) {
            return ollamaIntent;
        }
        return parseDeterministically(message);
    }

    private AiIntent parseWithOllama(String message) {
        return ollamaClient.generate(promptBuilder.buildIntentPrompt(message))
                .map(this::parseJsonIntent)
                .orElseGet(() -> AiIntent.builder().operation(AiOperation.UNKNOWN).build());
    }

    private AiIntent parseJsonIntent(String rawJson) {
        try {
            String json = rawJson == null ? "" : rawJson.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("\\s*```$", "");
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            String operationValue = String.valueOf(parsed.getOrDefault("operation", "UNKNOWN"));
            AiOperation operation = resolveOperation(operationValue);
            Map<String, Object> slots = parsed.get("slots") instanceof Map<?, ?> map
                    ? objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {})
                    : new LinkedHashMap<>();
            return AiIntent.builder().operation(operation).slots(slots).build();
        } catch (JsonProcessingException | RuntimeException ex) {
            return AiIntent.builder().operation(AiOperation.UNKNOWN).build();
        }
    }

    private AiIntent parseDeterministically(String message) {
        String text = message == null ? "" : message.trim();
        String lower = text.toLowerCase(Locale.ENGLISH);
        Map<String, Object> slots = new LinkedHashMap<>();

        if (lower.contains("create invoice") || lower.contains("make invoice")) {
            fillInvoiceSlots(text, slots);
            return intent(AiOperation.CREATE_INVOICE, slots);
        }
        if (lower.contains("record payment") || lower.contains("receive payment") || lower.contains("add payment")) {
            fillPaymentSlots(text, slots);
            return intent(AiOperation.RECORD_PAYMENT, slots);
        }
        if (lower.contains("create customer") || lower.contains("add customer")) {
            fillCustomerSlots(text, slots);
            return intent(AiOperation.CREATE_CUSTOMER, slots);
        }
        if (lower.contains("create product") || lower.contains("add product")) {
            fillProductSlots(text, slots);
            return intent(AiOperation.CREATE_PRODUCT, slots);
        }
        if (lower.contains("stock") || lower.contains("inventory of")) {
            slots.put("search", cleanupSearch(text.replaceAll("(?i)show|current|stock|of|inventory", " ")));
            fillChartSlots(lower, slots);
            return intent(AiOperation.CURRENT_STOCK, slots);
        }
        if (lower.contains("outstanding")) {
            slots.put("search", cleanupSearch(text.replaceAll("(?i)show|customers|customer|outstanding", " ")));
            return intent(AiOperation.OUTSTANDING_CUSTOMERS, slots);
        }
        if (lower.contains("collection")) {
            fillDateRangeSlots(lower, slots);
            fillChartSlots(lower, slots);
            return intent(AiOperation.COLLECTION_SUMMARY, slots);
        }
        if (lower.contains("expense")) {
            fillDateRangeSlots(lower, slots);
            fillChartSlots(lower, slots);
            return intent(AiOperation.EXPENSE_SUMMARY, slots);
        }
        if (lower.contains("profit")) {
            fillDateRangeSlots(lower, slots);
            fillChartSlots(lower, slots);
            return intent(AiOperation.PROFIT_SUMMARY, slots);
        }
        if (lower.contains("sales")) {
            fillDateRangeSlots(lower, slots);
            fillChartSlots(lower, slots);
            return intent(AiOperation.SALES_SUMMARY, slots);
        }
        if (lower.contains("invoice")) {
            slots.put("search", cleanupSearch(text.replaceAll("(?i)show|find|search|invoice|invoices", " ")));
            return intent(AiOperation.INVOICE_SEARCH, slots);
        }
        if (lower.contains("payment")) {
            slots.put("search", cleanupSearch(text.replaceAll("(?i)show|find|search|payment|payments", " ")));
            return intent(AiOperation.PAYMENT_SEARCH, slots);
        }
        if (lower.contains("product")) {
            slots.put("search", cleanupSearch(text.replaceAll("(?i)show|find|search|product|products", " ")));
            return intent(AiOperation.PRODUCT_SEARCH, slots);
        }
        if (lower.contains("customer")) {
            slots.put("search", cleanupSearch(text.replaceAll("(?i)show|find|search|customer|customers", " ")));
            return intent(AiOperation.CUSTOMER_SEARCH, slots);
        }
        if (lower.contains("inventory")) {
            fillChartSlots(lower, slots);
            return intent(AiOperation.INVENTORY_SUMMARY, slots);
        }
        return intent(AiOperation.UNKNOWN, slots);
    }

    private void fillInvoiceSlots(String text, Map<String, Object> slots) {
        Matcher matcher = Pattern.compile("(?i)create\\s+invoice\\s+for\\s+(.+?)\\s+with\\s+(\\d+)\\s+(.+)$").matcher(text);
        if (matcher.find()) {
            slots.put("customerName", cleanName(matcher.group(1)));
            slots.put("quantity", Integer.parseInt(matcher.group(2)));
            slots.put("productName", singular(cleanName(matcher.group(3))));
            return;
        }
        Matcher customer = Pattern.compile("(?i)for\\s+(.+?)(?:\\s+with|$)").matcher(text);
        if (customer.find()) {
            slots.put("customerName", cleanName(customer.group(1)));
        }
    }

    private void fillPaymentSlots(String text, Map<String, Object> slots) {
        Matcher payment = Pattern.compile("(?i)(?:record|receive|add)?\\s*payment\\s+of\\s+(?:rs\\.?|inr|\\p{Sc})?\\s*(\\d+(?:\\.\\d{1,2})?)\\s+(?:from|for)\\s+(.+?)(?:\\s+by\\s+(.+))?$").matcher(text);
        if (payment.find()) {
            slots.put("amount", new BigDecimal(payment.group(1)));
            slots.put("customerName", cleanName(payment.group(2)));
            if (payment.group(3) != null) {
                slots.put("paymentMode", cleanName(payment.group(3)));
            }
            return;
        }
        Matcher amount = MONEY_PATTERN.matcher(text);
        if (amount.find()) {
            slots.put("amount", new BigDecimal(amount.group(1)));
        }
    }

    private void fillCustomerSlots(String text, Map<String, Object> slots) {
        Matcher name = Pattern.compile("(?i)(?:create|add)\\s+customer\\s+(.+?)(?:\\s+mobile|\\s+phone|\\s+email|$)").matcher(text);
        if (name.find()) {
            slots.put("name", cleanName(name.group(1)));
        }
        Matcher mobile = MOBILE_PATTERN.matcher(text);
        if (mobile.find()) {
            slots.put("mobile", mobile.group(1));
        }
        Matcher email = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE).matcher(text);
        if (email.find()) {
            slots.put("email", email.group());
        }
    }

    private void fillProductSlots(String text, Map<String, Object> slots) {
        Matcher name = Pattern.compile("(?i)(?:create|add)\\s+product\\s+(.+?)(?:\\s+sku|\\s+category|\\s+price|\\s+stock|$)").matcher(text);
        if (name.find()) {
            slots.put("name", singular(cleanName(name.group(1))));
        }
        putRegex(text, slots, "sku", "(?i)sku\\s+([A-Za-z0-9_-]+)");
        putRegex(text, slots, "categoryName", "(?i)category\\s+([A-Za-z0-9 _-]+?)(?:\\s+sub|\\s+sku|\\s+price|\\s+stock|$)");
        putRegex(text, slots, "subCategoryName", "(?i)sub\\s*category\\s+([A-Za-z0-9 _-]+?)(?:\\s+sku|\\s+price|\\s+stock|$)");
        putNumberRegex(text, slots, "sellingPrice", "(?i)(?:selling\\s+price|price|rate)\\s+(\\d+(?:\\.\\d{1,2})?)");
        putNumberRegex(text, slots, "purchasePrice", "(?i)purchase\\s+price\\s+(\\d+(?:\\.\\d{1,2})?)");
        putIntegerRegex(text, slots, "stockQty", "(?i)stock\\s+(\\d+)");
        putIntegerRegex(text, slots, "minStockQty", "(?i)(?:min|minimum)\\s+stock\\s+(\\d+)");
        putNumberRegex(text, slots, "taxPercent", "(?i)tax\\s+(\\d+(?:\\.\\d{1,2})?)");
    }

    private void fillDateRangeSlots(String lower, Map<String, Object> slots) {
        if (lower.contains("monthly") || lower.contains("this month") || lower.contains("current month")) {
            slots.put("dateRange", "THIS_MONTH");
        }
        if (lower.contains("today")) {
            slots.put("dateRange", "TODAY");
        }
    }

    private void fillChartSlots(String lower, Map<String, Object> slots) {
        if (!(lower.contains("graph") || lower.contains("chart"))) {
            return;
        }
        if (lower.contains("pie")) {
            slots.put("chartType", "PIE");
            return;
        }
        if (lower.contains("bar")) {
            slots.put("chartType", "BAR");
            return;
        }
        if (lower.contains("line")) {
            slots.put("chartType", "LINE");
            return;
        }
        slots.put("chartType", "LINE");
    }

    private void putRegex(String text, Map<String, Object> slots, String key, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            slots.put(key, cleanName(matcher.group(1)));
        }
    }

    private void putNumberRegex(String text, Map<String, Object> slots, String key, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            slots.put(key, new BigDecimal(matcher.group(1)));
        }
    }

    private void putIntegerRegex(String text, Map<String, Object> slots, String key, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            slots.put(key, Integer.parseInt(matcher.group(1)));
        }
    }

    private AiIntent intent(AiOperation operation, Map<String, Object> slots) {
        return AiIntent.builder().operation(operation).slots(slots).build();
    }

    private AiOperation resolveOperation(String value) {
        try {
            return AiOperation.valueOf(value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ENGLISH));
        } catch (RuntimeException ex) {
            return AiOperation.UNKNOWN;
        }
    }

    private String cleanupSearch(String value) {
        String cleaned = cleanName(value);
        return cleaned.isBlank() ? null : singular(cleaned);
    }

    private String cleanName(String value) {
        return value == null ? "" : value
                .replaceAll("(?i)\\bplease\\b", "")
                .replaceAll("[?.!]+$", "")
                .trim();
    }

    private String singular(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 3 && trimmed.toLowerCase(Locale.ENGLISH).endsWith("s") && !trimmed.toLowerCase(Locale.ENGLISH).endsWith("ss")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
