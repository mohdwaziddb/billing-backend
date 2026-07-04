package com.billing.ai.prompts;

import com.billing.ai.dto.AiChatRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiPromptBuilder {

    public String buildIntentPrompt(String message) {
        return buildIntentPrompt(message, List.of());
    }

    public String buildIntentPrompt(String message, List<AiChatRequest.HistoryMessage> history) {
        return """
                You are an intent parser for Bizio Billing SaaS.
                Return only valid JSON. Do not include markdown, commentary, or explanations.
                Do not include thinking text. Do not wrap the JSON in code fences.

                Supported operations:
                CUSTOMER_SEARCH, PRODUCT_SEARCH, CURRENT_STOCK, OUTSTANDING_CUSTOMERS,
                INVOICE_SEARCH, PAYMENT_SEARCH, SALES_SUMMARY, COLLECTION_SUMMARY,
                EXPENSE_SUMMARY, INVENTORY_SUMMARY, PROFIT_SUMMARY,
                CREATE_CUSTOMER, CREATE_PRODUCT, CREATE_INVOICE, RECORD_PAYMENT, UNKNOWN.

                JSON schema:
                {
                  "operation": "CREATE_INVOICE",
                  "slots": {
                    "customerName": "Ram",
                    "productName": "Fan",
                    "quantity": 5,
                    "amount": 5000,
                    "mobile": "9999999999",
                    "search": "Ram",
                    "paymentMode": "Cash",
                    "dateRange": "THIS_MONTH",
                    "chartType": "LINE"
                  }
                }

                Normalize plural product names to singular when obvious. Keep names as typed by the user.
                For dateRange use one of: TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR.
                For chartType use one of: LINE, BAR, PIE when the user asks for graph, chart, trend, breakdown, visual, or plot.
                Use PIE for category-wise, split, share, mix, or breakdown questions unless the user explicitly asks for bar/line.
                Examples:
                "aaj ki sale batao" -> {"operation":"SALES_SUMMARY","slots":{"dateRange":"TODAY"}}
                "last month ka total sale graph me batao" -> {"operation":"SALES_SUMMARY","slots":{"dateRange":"LAST_MONTH","chartType":"LINE"}}
                "category wise sale pie chart" -> {"operation":"SALES_SUMMARY","slots":{"chartType":"PIE"}}
                "is month ka kharcha" -> {"operation":"EXPENSE_SUMMARY","slots":{"dateRange":"THIS_MONTH"}}
                "this week collection bar graph" -> {"operation":"COLLECTION_SUMMARY","slots":{"dateRange":"THIS_WEEK","chartType":"BAR"}}
                "baaki payment kiski hai" -> {"operation":"OUTSTANDING_CUSTOMERS","slots":{}}
                "stock dikhao" -> {"operation":"CURRENT_STOCK","slots":{}}
                "customer Ram search karo" -> {"operation":"CUSTOMER_SEARCH","slots":{"search":"Ram"}}

                Use the conversation history to resolve follow-up questions.
                If the user says "aur aaj ka", "this month ka", "uska", or a similarly short follow-up,
                infer the operation/search topic from the most recent relevant user or assistant message.
                If the latest message only changes chart type, such as "pie chart me" or "bar graph", keep the previous operation and previous dateRange.
                The user may write in English, Hindi, Hinglish, or mixed language. Understand the meaning, not only exact words.
                If the latest message is a follow-up, keep the same topic from history unless the latest message clearly changes it.

                Conversation history:
                """ + formatHistory(history) + """

                User message:
                """ + message;
    }

    public String buildGeneralChatPrompt(String message, List<AiChatRequest.HistoryMessage> history) {
        return """
                You are Bizio AI Assistant for a billing SaaS app.
                Reply naturally to normal user messages that are not billing commands.
                Keep the reply short, friendly, and useful. Match the user's language when possible.
                Use the conversation history first. If the latest user message is a follow-up, answer in relation to the previous relevant question.
                The user may write in English, Hindi, Hinglish, or mixed language; reply in the same style as the latest user message.
                Do not return JSON, markdown tables, code fences, or internal reasoning.
                Do not claim access to live internet, live weather, or external real-time data.
                If the user asks for live weather, say you cannot fetch live weather here and ask for a city or suggest checking a weather service.
                If the user wants billing help, suggest examples such as sales summary, stock, outstanding customers, customer search, invoice search, or expense summary.

                Conversation history:
                """ + formatHistory(history) + """

                User message:
                """ + message;
    }

    private String formatHistory(List<AiChatRequest.HistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        history.stream()
                .filter(item -> item != null && item.getContent() != null && !item.getContent().isBlank())
                .limit(12)
                .forEach(item -> builder
                        .append("- ")
                        .append(normalizeRole(item.getRole()))
                        .append(": ")
                        .append(item.getContent().replaceAll("\\s+", " ").trim())
                        .append("\n"));
        return builder.isEmpty() ? "[]" : builder.toString();
    }

    private String normalizeRole(String role) {
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }
}
