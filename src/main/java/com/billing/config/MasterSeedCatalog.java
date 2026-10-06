package com.billing.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SINGLE source of truth for every master/seed row in the project.
 * Menus, actions, roles, default taxes, theme and notification channels
 * are defined here ONCE and every provisioning/repair/startup flow reads
 * from this catalog. Do NOT duplicate these lists anywhere else —
 * drift between copies is what breaks new tenants.
 */
public final class MasterSeedCatalog {

    private MasterSeedCatalog() {
    }

    public record MenuSeed(String name, String code, String icon, String route, int order, String parentCode) {
    }

    public record ActionSeed(String name, String code) {
    }

    public record RoleSeed(String name, String code) {
    }

    public static final List<RoleSeed> ROLES = List.of(
            new RoleSeed("Owner", "OWNER"),
            new RoleSeed("Admin", "ADMIN"),
            new RoleSeed("User", "USER"));

    public static final List<MenuSeed> MENUS = List.of(
            new MenuSeed("Dashboard", "DASHBOARD", "LayoutDashboard", "/dashboard", 1, null),
            new MenuSeed("Customers", "CUSTOMERS", "Users", "/customers", 2, null),
            new MenuSeed("Inventory", "INVENTORY", "Package", "/inventory", 3, null),
            new MenuSeed("Products", "PRODUCTS", "Boxes", "/products", 4, "INVENTORY"),
            new MenuSeed("Purchases", "PURCHASES", "ShoppingCart", "/purchases", 5, "INVENTORY"),
            new MenuSeed("Stock Ledger", "STOCK_LEDGER", "ScrollText", "/inventory/stock-ledger", 6, "INVENTORY"),
            new MenuSeed("Create Invoice", "CREATE_INVOICE", "FilePlus2", "/create-invoice", 7, null),
            new MenuSeed("Invoices", "INVOICES", "FileText", "/invoices", 8, null),
            new MenuSeed("Payments", "PAYMENTS", "CreditCard", "/payments", 9, null),
            new MenuSeed("Expenses", "EXPENSES", "ReceiptIndianRupee", "/expenses", 10, null),
            new MenuSeed("Outstanding", "OUTSTANDING", "Wallet", "/outstanding", 11, null),
            new MenuSeed("Analytics", "ANALYTICS", "BarChart3", "/analytics", 12, null),
            new MenuSeed("DataPort", "DATA_PORT", "FileText", "/data-port", 13, null),
            new MenuSeed("Product DataPort", "PRODUCT_DATAPORT", "Boxes", "/data-port/products", 14, "DATA_PORT"),
            new MenuSeed("Reports", "REPORTS", "BarChart3", "/reports", 15, null),
            new MenuSeed("Profit & Loss", "PROFIT_LOSS", "TrendingUp", "/reports/profit-loss", 16, "REPORTS"),
            new MenuSeed("Setup", "SETUP", "Settings", "/setup", 17, null),
            new MenuSeed("Users", "USERS", "Users", "/setup/users", 18, "SETUP"),
            new MenuSeed("Product Categories", "PRODUCT_CATEGORY", "Tags", "/setup/product-categories", 19, "SETUP"),
            new MenuSeed("Product Sub Categories", "PRODUCT_SUB_CATEGORIES", "Tags", "/setup/product-sub-categories", 20, "SETUP"),
            new MenuSeed("Expense Categories", "EXPENSE_CATEGORIES", "Tags", "/setup/expense-categories", 21, "SETUP"),
            new MenuSeed("Payment Modes", "PAYMENT_MODES", "CreditCard", "/setup/payment-modes", 22, "SETUP"),
            new MenuSeed("Theme Settings", "THEME_SETTINGS", "Palette", "/setup/theme-settings", 23, "SETUP"),
            new MenuSeed("About Company", "ABOUT_COMPANY", "Building2", "/setup/about-company", 24, "SETUP"),
            new MenuSeed("Email Templates", "EMAIL_TEMPLATES", "Mail", "/setup/email-templates", 25, "SETUP"),
            new MenuSeed("SMS Templates", "SMS_TEMPLATES", "Mail", "/setup/sms-templates", 26, "SETUP"),
            new MenuSeed("Communication", "COMMUNICATION", "Mail", "/setup/communication", 27, "SETUP"),
            new MenuSeed("Role Permissions", "ROLE_PERMISSIONS", "ShieldCheck", "/setup/role-permissions", 28, "SETUP"),
            new MenuSeed("Tax Master", "TAX_MASTER", "ReceiptIndianRupee", "/setup/tax-master", 29, "SETUP"),
            new MenuSeed("Invoice Templates", "INVOICE_TEMPLATES", "ReceiptText", "/setup/invoice-templates", 30, "SETUP"),
            new MenuSeed("Payment Hierarchy", "PAYMENT_HIERARCHY", "CreditCard", "/reports/payment-hierarchy", 31, "REPORTS"),
            new MenuSeed("Sales Referrals", "SALES_REFERRALS", "TrendingUp", "/reports/sales-referrals", 32, "REPORTS"),
            new MenuSeed("GST Summary", "GST_SUMMARY", "ReceiptIndianRupee", "/reports/gst-summary", 33, "REPORTS"));

    public static final List<ActionSeed> ACTIONS = List.of(
            new ActionSeed("View", "VIEW"),
            new ActionSeed("Add", "ADD"),
            new ActionSeed("Edit", "EDIT"),
            new ActionSeed("Delete", "DELETE"),
            new ActionSeed("Restore", "RESTORE"),
            new ActionSeed("Export", "EXPORT"),
            new ActionSeed("Show Logs", "LOGS"),
            new ActionSeed("Show Logs", "VIEW_LOGS"),
            new ActionSeed("Preview", "PREVIEW"),
            new ActionSeed("Change Default", "CHANGE_DEFAULT"),
            new ActionSeed("Send Email", "EMAIL_SEND"),
            new ActionSeed("Send SMS", "SMS_SEND"),
            new ActionSeed("Send WhatsApp", "WHATSAPP_SEND"));

    /** Menus retired from the product but kept in DB as inactive. */
    public static final List<String> RETIRED_MENU_CODES = List.of(
            "AUDIT_LOG_VIEW",
            "NOTIFICATION_SETTINGS",
            "MANAGEMENT_HIERARCHY",
            "EMAIL_SETTINGS",
            "SMS_SETTINGS",
            "WHATSAPP_SETTINGS");

    /** Default GST slabs seeded per company; first entry is the default tax. */
    public static final List<BigDecimal> DEFAULT_TAX_RATES = List.of(
            BigDecimal.ZERO,
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(12),
            BigDecimal.valueOf(18),
            BigDecimal.valueOf(28));

    public static final String DEFAULT_THEME_COLOR = "#0EA5E9";

    public static final List<String> DEFAULT_NOTIFICATION_CHANNELS = List.of("Email");

    /** Default per-tenant super-admin login (password always BCrypt-hashed at rest). */
    public static final String DEFAULT_SUPER_ADMIN_USERNAME = "@biziotechnologies";

    /** Role -> visible menu codes (permission policy, single copy). */
    public static final Map<String, Set<String>> VISIBLE_MENUS_BY_ROLE = Map.of(
            "OWNER", Set.of("DASHBOARD", "CUSTOMERS", "INVENTORY", "PRODUCTS", "PURCHASES", "STOCK_LEDGER", "CREATE_INVOICE", "INVOICES", "PAYMENTS", "EXPENSES", "OUTSTANDING", "ANALYTICS", "DATA_PORT", "PRODUCT_DATAPORT", "REPORTS", "PROFIT_LOSS", "SETUP", "USERS", "PRODUCT_CATEGORY", "PRODUCT_SUB_CATEGORIES", "EXPENSE_CATEGORIES", "PAYMENT_MODES", "THEME_SETTINGS", "ABOUT_COMPANY", "EMAIL_TEMPLATES", "SMS_TEMPLATES", "COMMUNICATION", "ROLE_PERMISSIONS", "TAX_MASTER", "INVOICE_TEMPLATES", "PAYMENT_HIERARCHY", "SALES_REFERRALS", "GST_SUMMARY", "AI_ASSISTANT"),
            "ADMIN", Set.of("DASHBOARD", "CUSTOMERS", "INVENTORY", "PRODUCTS", "PURCHASES", "STOCK_LEDGER", "CREATE_INVOICE", "INVOICES", "PAYMENTS", "EXPENSES", "OUTSTANDING", "ANALYTICS", "DATA_PORT", "PRODUCT_DATAPORT", "REPORTS", "PROFIT_LOSS", "SETUP", "PRODUCT_CATEGORY", "PRODUCT_SUB_CATEGORIES", "EXPENSE_CATEGORIES", "PAYMENT_MODES", "ABOUT_COMPANY", "EMAIL_TEMPLATES", "SMS_TEMPLATES", "COMMUNICATION", "TAX_MASTER", "INVOICE_TEMPLATES", "GST_SUMMARY", "AI_ASSISTANT"),
            "USER", Set.of("DASHBOARD", "CUSTOMERS", "INVENTORY", "PRODUCTS", "STOCK_LEDGER", "CREATE_INVOICE", "INVOICES", "OUTSTANDING", "ANALYTICS", "ABOUT_COMPANY", "AI_ASSISTANT"));

    /** Role -> allowed action codes (permission policy, single copy). */
    public static final Map<String, Set<String>> ACTION_CODES_BY_ROLE = Map.of(
            "OWNER", Set.of("VIEW", "ADD", "EDIT", "DELETE", "RESTORE", "EXPORT", "LOGS", "VIEW_LOGS", "PREVIEW", "CHANGE_DEFAULT", "EMAIL_SEND", "SMS_SEND", "WHATSAPP_SEND"),
            "ADMIN", Set.of("VIEW", "ADD", "EDIT", "DELETE", "RESTORE", "EXPORT", "LOGS", "VIEW_LOGS", "PREVIEW", "CHANGE_DEFAULT", "EMAIL_SEND", "SMS_SEND", "WHATSAPP_SEND"),
            "USER", Set.of("VIEW"));

    /** Menus where non-owners keep VIEW at most. */
    public static final Set<String> RESTRICTED_OWNER_ONLY_MENUS = Set.of(
            "PRODUCT_CATEGORY", "PRODUCT_SUB_CATEGORIES", "PAYMENT_MODES", "TAX_MASTER");

    public static boolean isMenuVisible(String roleCode, String menuCode) {
        Set<String> visible = VISIBLE_MENUS_BY_ROLE.getOrDefault(roleCode, Set.of());
        return visible.contains(menuCode);
    }

    /**
     * Exact permission algorithm shared by the runtime seeder and the
     * offline sample.sql generator. Rule order mirrors the seeder:
     * matrix -> restricted-owner-only -> about/theme -> user-create-invoice-add.
     */
    public static boolean isActionAllowed(String roleCode, String menuCode, String actionCode) {
        boolean canView = isMenuVisible(roleCode, menuCode);
        boolean allowed = canView
                && ACTION_CODES_BY_ROLE.getOrDefault(roleCode, Set.of()).contains(actionCode);
        if (RESTRICTED_OWNER_ONLY_MENUS.contains(menuCode) && !"OWNER".equals(roleCode)) {
            allowed = "VIEW".equals(actionCode);
        }
        if (("ABOUT_COMPANY".equals(menuCode) || "THEME_SETTINGS".equals(menuCode)) && !"OWNER".equals(roleCode)) {
            allowed = "ABOUT_COMPANY".equals(menuCode) && "VIEW".equals(actionCode);
        }
        if ("USER".equals(roleCode) && "CREATE_INVOICE".equals(menuCode) && "ADD".equals(actionCode)) {
            allowed = true;
        }
        return allowed;
    }
}
