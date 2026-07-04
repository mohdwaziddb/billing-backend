package com.billing.ai.parser;

import com.billing.ai.dto.AiChatRequest;
import com.billing.ai.prompts.AiPromptBuilder;
import com.billing.ai.service.OllamaClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiIntentParser {

    private static final Logger log = LoggerFactory.getLogger(AiIntentParser.class);

    private static final Pattern MONEY_PATTERN = Pattern.compile("(?:rs\\.?|inr|\\p{Sc})?\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILE_PATTERN = Pattern.compile("\\b(\\d{10})\\b");

    private final OllamaClient ollamaClient;
    private final AiPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public AiIntent parse(String message) {
        return parse(message, List.of());
    }

    public AiIntent parse(String message, List<AiChatRequest.HistoryMessage> history) {
        AiIntent ollamaIntent = parseWithOllama(message, history);
        if (ollamaIntent.getOperation() != AiOperation.UNKNOWN) {
            enrichDeterministicSlots(message, history, ollamaIntent.getSlots());
            return ollamaIntent;
        }
        return parseDeterministically(message, history);
    }

    private AiIntent parseWithOllama(String message, List<AiChatRequest.HistoryMessage> history) {
        return ollamaClient.generate(promptBuilder.buildIntentPrompt(message, history))
                .map(this::parseJsonIntent)
                .orElseGet(() -> AiIntent.builder().operation(AiOperation.UNKNOWN).build());
    }

    private AiIntent parseJsonIntent(String rawJson) {
        try {
            String json = extractJsonObject(rawJson);
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            String operationValue = String.valueOf(parsed.getOrDefault("operation", "UNKNOWN"));
            AiOperation operation = resolveOperation(operationValue);
            Map<String, Object> slots = parsed.get("slots") instanceof Map<?, ?> map
                    ? objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {})
                    : new LinkedHashMap<>();
            return AiIntent.builder().operation(operation).slots(slots).build();
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Unable to parse Ollama intent JSON: {}", ex.getMessage());
            return AiIntent.builder().operation(AiOperation.UNKNOWN).build();
        }
    }

    private AiIntent parseDeterministically(String message, List<AiChatRequest.HistoryMessage> history) {
        String text = message == null ? "" : message.trim();
        String lower = text.toLowerCase(Locale.ENGLISH);
        Map<String, Object> slots = new LinkedHashMap<>();
        fillResponseLanguageSlot(text, history, slots);
        AiOperation followUpOperation = inferFollowUpOperation(lower, history);
        if (followUpOperation != AiOperation.UNKNOWN) {
            fillFollowUpContextSlots(history, slots);
            fillDateRangeSlots(lower, slots);
            fillChartSlots(lower, slots);
            return intent(followUpOperation, slots);
        }

        if (hasAny(lower, "create invoice", "make invoice", "invoice banao", "bill banao")) {
            fillInvoiceSlots(text, slots);
            return intent(AiOperation.CREATE_INVOICE, slots);
        }
        if (hasAny(lower, "record payment", "receive payment", "add payment", "payment add", "payment entry")) {
            fillPaymentSlots(text, slots);
            return intent(AiOperation.RECORD_PAYMENT, slots);
        }
        if (hasAny(lower, "create customer", "add customer", "customer add", "customer banao")) {
            fillCustomerSlots(text, slots);
            return intent(AiOperation.CREATE_CUSTOMER, slots);
        }
        if (hasAny(lower, "create product", "add product", "product add", "product banao")) {
            fillProductSlots(text, slots);
            return intent(AiOperation.CREATE_PRODUCT, slots);
        }
        if (hasAny(lower, "stock", "inventory of", "maal", "quantity", "qty", "available")) {
            slots.put("search", cleanupSearch(text.replaceAll("(?i)show|current|stock|of|inventory", " ")));
            fillChartSlots(lower, slots);
            return intent(AiOperation.CURRENT_STOCK, slots);
        }
        if (hasAny(lower, "outstanding", "due", "baaki", "udhaar")) {
            slots.put("search", cleanupSearch(text.replaceAll("(?i)show|customers|customer|outstanding", " ")));
            return intent(AiOperation.OUTSTANDING_CUSTOMERS, slots);
        }
        if (hasAny(lower, "collection", "collected", "vasooli", "payment received")) {
            fillDateRangeSlots(lower, slots);
            fillChartSlots(lower, slots);
            return intent(AiOperation.COLLECTION_SUMMARY, slots);
        }
        if (hasAny(lower, "expense", "kharcha", "expenses", "spend", "spent")) {
            fillDateRangeSlots(lower, slots);
            fillChartSlots(lower, slots);
            return intent(AiOperation.EXPENSE_SUMMARY, slots);
        }
        if (hasAny(lower, "profit", "munafa", "margin", "kamai")) {
            fillDateRangeSlots(lower, slots);
            fillChartSlots(lower, slots);
            return intent(AiOperation.PROFIT_SUMMARY, slots);
        }
        if (hasAny(lower, "sales", "sale", "revenue", "bikri", "business", "turnover", "income", "earning", "becha")) {
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
        if (hasAny(lower, "inventory", "stock summary")) {
            fillChartSlots(lower, slots);
            return intent(AiOperation.INVENTORY_SUMMARY, slots);
        }
        return intent(AiOperation.UNKNOWN, slots);
    }

    private void enrichDeterministicSlots(String message, List<AiChatRequest.HistoryMessage> history, Map<String, Object> slots) {
        if (slots == null) {
            return;
        }
        String lower = message == null ? "" : message.trim().toLowerCase(Locale.ENGLISH);
        if (isFollowUp(lower)) {
            fillFollowUpContextSlots(history, slots);
        }
        fillDateRangeSlots(lower, slots);
        fillChartSlots(lower, slots);
        fillResponseLanguageSlot(message, history, slots);
    }

    private AiOperation inferFollowUpOperation(String lower, List<AiChatRequest.HistoryMessage> history) {
        if (!isFollowUp(lower) || history == null || history.isEmpty()) {
            return AiOperation.UNKNOWN;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            AiChatRequest.HistoryMessage item = history.get(index);
            if (item == null || item.getContent() == null) {
                continue;
            }
            AiOperation operation = operationFromText(item.getContent().toLowerCase(Locale.ENGLISH));
            if (operation != AiOperation.UNKNOWN) {
                return operation;
            }
        }
        return AiOperation.UNKNOWN;
    }

    private boolean isFollowUp(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        return hasAny(lower, "aur", "also", "same", "uska", "iska", "iske", "uske", "woh", "yeh",
                "again", "repeat", "compare", "comparison", "graph", "chart", "trend", "bar", "pie", "line",
                "aaj", "today", "kal", "yesterday",
                "this week", "last week", "this month", "is month", "iss month", "last month", "pichle month",
                "previous month", "this year", "last year")
                && !hasAny(lower, "customer", "product", "invoice", "payment", "stock", "sale", "sales", "expense", "profit", "collection", "outstanding", "bikri", "kharcha", "baaki");
    }

    private AiOperation operationFromText(String lower) {
        if (hasAny(lower, "sales summary", "total sales", "sale", "sales", "revenue", "bikri", "business", "turnover", "सेल", "बिक्री")) {
            return AiOperation.SALES_SUMMARY;
        }
        if (hasAny(lower, "collection summary", "collection", "collected", "vasooli", "वसूली", "कलेक्शन")) {
            return AiOperation.COLLECTION_SUMMARY;
        }
        if (hasAny(lower, "expense summary", "expense", "kharcha", "spent", "खर्च", "खर्चा")) {
            return AiOperation.EXPENSE_SUMMARY;
        }
        if (hasAny(lower, "profit summary", "profit", "munafa", "kamai", "मुनाफा", "कमाई")) {
            return AiOperation.PROFIT_SUMMARY;
        }
        if (hasAny(lower, "outstanding customers", "outstanding", "baaki", "udhaar", "due", "बाकी", "उधार")) {
            return AiOperation.OUTSTANDING_CUSTOMERS;
        }
        if (hasAny(lower, "current stock", "stock", "inventory")) {
            return AiOperation.CURRENT_STOCK;
        }
        return AiOperation.UNKNOWN;
    }

    private void fillFollowUpContextSlots(List<AiChatRequest.HistoryMessage> history, Map<String, Object> slots) {
        if (history == null || history.isEmpty()) {
            return;
        }
        Map<String, Object> contextSlots = new LinkedHashMap<>();
        for (int index = Math.max(0, history.size() - 8); index < history.size(); index++) {
            AiChatRequest.HistoryMessage item = history.get(index);
            if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                continue;
            }
            String lower = item.getContent().toLowerCase(Locale.ENGLISH);
            fillDateRangeSlots(lower, contextSlots);
            fillChartSlots(lower, contextSlots);
        }
        contextSlots.forEach(slots::putIfAbsent);
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
        Matcher name = Pattern.compile("(?i)(?:create|add)\\s+product\\s+(.+?)(?:\\s+sku|\\s+category|\\s+tax|\\s+minimum|$)").matcher(text);
        if (name.find()) {
            slots.put("name", singular(cleanName(name.group(1))));
        }
        putRegex(text, slots, "sku", "(?i)sku\\s+([A-Za-z0-9_-]+)");
        putRegex(text, slots, "categoryName", "(?i)category\\s+([A-Za-z0-9 _-]+?)(?:\\s+sub|\\s+sku|\\s+tax|\\s+minimum|$)");
        putRegex(text, slots, "subCategoryName", "(?i)sub\\s*category\\s+([A-Za-z0-9 _-]+?)(?:\\s+sku|\\s+tax|\\s+minimum|$)");
        putIntegerRegex(text, slots, "minStockQty", "(?i)(?:min|minimum)\\s+stock\\s+(\\d+)");
        putNumberRegex(text, slots, "taxPercent", "(?i)tax\\s+(\\d+(?:\\.\\d{1,2})?)");
    }

    private void fillDateRangeSlots(String lower, Map<String, Object> slots) {
        if (hasAny(lower, "yesterday", "kal ka", "kal ki", "kal ke", "कल")) {
            slots.put("dateRange", "YESTERDAY");
        }
        if (hasAny(lower, "today", "aaj", "आज")) {
            slots.put("dateRange", "TODAY");
        }
        if (hasAny(lower, "last week", "previous week", "pichle week", "pichla week", "pichle hafte")) {
            slots.put("dateRange", "LAST_WEEK");
        }
        if (hasAny(lower, "this week", "current week", "is week", "iss week", "is hafte", "iss hafte")) {
            slots.put("dateRange", "THIS_WEEK");
        }
        if (hasAny(lower, "last month", "previous month", "pichle month", "pichla month", "last mahina", "pichle mahine")) {
            slots.put("dateRange", "LAST_MONTH");
        }
        if (hasAny(lower, "monthly", "this month", "current month", "is month", "iss month", "mahina")) {
            slots.put("dateRange", "THIS_MONTH");
        }
        if (hasAny(lower, "last year", "previous year", "pichle year", "pichla saal", "last saal")) {
            slots.put("dateRange", "LAST_YEAR");
        }
        if (hasAny(lower, "this year", "current year", "is year", "iss year", "is saal", "iss saal")) {
            slots.put("dateRange", "THIS_YEAR");
        }
    }

    private void fillResponseLanguageSlot(String message, List<AiChatRequest.HistoryMessage> history, Map<String, Object> slots) {
        String combined = ((message == null ? "" : message) + " " + recentUserHistory(history)).toLowerCase(Locale.ENGLISH);
        if (Pattern.compile("\\p{InDevanagari}").matcher(combined).find()
                || hasAny(combined, "bata", "bta", "kya", "kaise", "kitna", "kitni", "dikhao", "banao", "hai", "nahi", "haan", "bhai", "pichle", "aaj", "kal", "mahina", "saal")) {
            slots.put("responseLanguage", "HI");
            return;
        }
        slots.put("responseLanguage", "EN");
    }

    private String recentUserHistory(List<AiChatRequest.HistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = Math.max(0, history.size() - 4); index < history.size(); index++) {
            AiChatRequest.HistoryMessage item = history.get(index);
            if (item != null && !"assistant".equalsIgnoreCase(item.getRole()) && item.getContent() != null) {
                builder.append(' ').append(item.getContent());
            }
        }
        return builder.toString();
    }

    private void fillChartSlots(String lower, Map<String, Object> slots) {
        if (lower.contains("pie")) {
            slots.put("chartType", "PIE");
            return;
        }
        boolean asksForVisual = hasAny(lower, "graph", "chart", "trend", "visual", "plot");
        if (!asksForVisual) {
            if (hasAny(lower, "bar me", "bar mein", "bar chart")) {
                slots.put("chartType", "BAR");
                return;
            }
            if (hasAny(lower, "line me", "line mein", "line chart")) {
                slots.put("chartType", "LINE");
            }
            return;
        }
        if (lower.contains("bar") || lower.contains("column")) {
            slots.put("chartType", "BAR");
            return;
        }
        if (lower.contains("line") || lower.contains("trend")) {
            slots.put("chartType", "LINE");
            return;
        }
        if (hasAny(lower, "category wise", "category-wise", "breakdown", "split", "mix", "share")) {
            slots.put("chartType", "PIE");
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

    private String extractJsonObject(String rawJson) {
        String value = rawJson == null ? "" : rawJson.trim()
                .replaceAll("(?is)<think>.*?</think>", "")
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private boolean hasAny(String value, String... keywords) {
        if (value == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
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
