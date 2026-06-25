package com.billing.ai.prompts;

import org.springframework.stereotype.Service;

@Service
public class AiPromptBuilder {

    public String buildIntentPrompt(String message) {
        return """
                You are an intent parser for BizFinity Billing SaaS.
                Return only valid JSON. Do not include markdown, commentary, or explanations.

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
                    "dateRange": "THIS_MONTH"
                  }
                }

                Normalize plural product names to singular when obvious. Keep names as typed by the user.
                User message:
                """ + message;
    }
}
