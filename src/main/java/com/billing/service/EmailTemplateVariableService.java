package com.billing.service;

import com.billing.entity.Company;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EmailTemplateVariableService {

    public static final Map<String, String> AVAILABLE_VARIABLES = Map.ofEntries(
            Map.entry("Customer_Name", "Customer name"),
            Map.entry("Customer_Email", "Customer email"),
            Map.entry("Customer_Mobile", "Customer mobile"),
            Map.entry("Outstanding_Amount", "Outstanding amount"),
            Map.entry("Invoice_Number", "Invoice number"),
            Map.entry("Invoice_Date", "Invoice date"),
            Map.entry("Invoice_Total", "Invoice total"),
            Map.entry("Due_Date", "Due date"),
            Map.entry("Company_Name", "Company name"),
            Map.entry("Company_Email", "Company email"),
            Map.entry("Company_Phone", "Company phone"),
            Map.entry("Current_Date", "Current date")
    );

    public Map<String, Object> baseVariables(Company company) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("Company_Name", company.getName());
        variables.put("Company_Email", company.getEmail());
        variables.put("Company_Phone", company.getPhone());
        variables.put("Current_Date", com.billing.core.AppDateTime.formatDate(com.billing.core.AppDateTime.istToday()));
        return variables;
    }

    /** Plain text (email subject, SMS, WhatsApp): keys waise hi replace. */
    public String render(String template, Company company, Map<String, Object> inputVariables) {
        return render(template, company, inputVariables, false);
    }

    /**
     * Email HTML body: values HTML-escape hote hain, taaki customer name jaise
     * fields me daala gaya HTML/link company ke email me inject na ho.
     */
    public String renderHtml(String template, Company company, Map<String, Object> inputVariables) {
        return render(template, company, inputVariables, true);
    }

    private String render(String template, Company company, Map<String, Object> inputVariables, boolean escapeHtml) {
        String rendered = template == null ? "" : template;
        Map<String, Object> variables = baseVariables(company);
        if (inputVariables != null) {
            variables.putAll(inputVariables);
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replace(placeholder, escapeHtml ? HtmlUtils.htmlEscape(value) : value);
        }
        return rendered;
    }
}
