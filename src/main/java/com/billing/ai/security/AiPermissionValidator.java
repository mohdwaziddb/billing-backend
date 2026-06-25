package com.billing.ai.security;

import com.billing.ai.context.AiUserContext;
import com.billing.ai.parser.AiOperation;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AiPermissionValidator {

    public Optional<String> denialMessage(AiUserContext context, AiOperation operation) {
        RequiredPermission permission = requiredPermission(operation);
        if (permission == null) {
            return Optional.empty();
        }
        if (context.hasPermission(permission.menuCode(), permission.actionCode())) {
            return Optional.empty();
        }
        return Optional.of(permission.denialMessage());
    }

    private RequiredPermission requiredPermission(AiOperation operation) {
        return switch (operation) {
            case CUSTOMER_SEARCH -> permission("CUSTOMERS", "VIEW", "You do not have permission to view customers.");
            case PRODUCT_SEARCH, CURRENT_STOCK, INVENTORY_SUMMARY -> permission("PRODUCTS", "VIEW", "You do not have permission to view products.");
            case OUTSTANDING_CUSTOMERS -> permission("OUTSTANDING", "VIEW", "You do not have permission to view outstanding customers.");
            case INVOICE_SEARCH -> permission("INVOICES", "VIEW", "You do not have permission to view invoices.");
            case PAYMENT_SEARCH, COLLECTION_SUMMARY -> permission("PAYMENTS", "VIEW", "You do not have permission to view payments.");
            case SALES_SUMMARY -> permission("DASHBOARD", "VIEW", "You do not have permission to view sales summary.");
            case EXPENSE_SUMMARY -> permission("EXPENSES", "VIEW", "You do not have permission to view expenses.");
            case PROFIT_SUMMARY -> permission("PROFIT_LOSS", "VIEW", "You do not have permission to view profit summary.");
            case CREATE_CUSTOMER -> permission("CUSTOMERS", "ADD", "You do not have permission to create customers.");
            case CREATE_PRODUCT -> permission("PRODUCTS", "ADD", "You do not have permission to create products.");
            case CREATE_INVOICE -> permission("CREATE_INVOICE", "ADD", "You do not have permission to create invoices.");
            case RECORD_PAYMENT -> permission("PAYMENTS", "ADD", "You do not have permission to record payments.");
            case UNKNOWN -> null;
        };
    }

    private RequiredPermission permission(String menuCode, String actionCode, String denialMessage) {
        return new RequiredPermission(menuCode, actionCode, denialMessage);
    }

    private record RequiredPermission(String menuCode, String actionCode, String denialMessage) {
    }
}
