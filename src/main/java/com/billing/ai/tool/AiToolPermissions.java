package com.billing.ai.tool;

import java.util.Map;

public final class AiToolPermissions {

    public record Requirement(String menu, String action) {
    }

    public static final Map<String, Requirement> BY_TOOL = Map.ofEntries(
            Map.entry("get_sales_summary", new Requirement("ANALYTICS", "VIEW")),
            Map.entry("get_business_growth_report", new Requirement("ANALYTICS", "VIEW")),
            Map.entry("get_top_products", new Requirement("ANALYTICS", "VIEW")),
            Map.entry("get_daily_collection_chart", new Requirement("ANALYTICS", "VIEW")),
            Map.entry("get_customer_dues", new Requirement("OUTSTANDING", "VIEW")),
            Map.entry("list_customers", new Requirement("CUSTOMERS", "VIEW")),
            Map.entry("find_customer", new Requirement("CUSTOMERS", "VIEW")),
            Map.entry("find_product", new Requirement("PRODUCTS", "VIEW")),
            Map.entry("get_customer_invoices", new Requirement("INVOICES", "VIEW")),
            Map.entry("get_invoice_detail", new Requirement("INVOICES", "VIEW")),
            Map.entry("create_customer", new Requirement("CUSTOMERS", "ADD")),
            Map.entry("create_invoice", new Requirement("CREATE_INVOICE", "ADD")),
            Map.entry("create_payment", new Requirement("PAYMENTS", "ADD"))
    );

    private AiToolPermissions() {
    }
}
