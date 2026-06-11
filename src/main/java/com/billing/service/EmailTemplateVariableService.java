package com.billing.service;

import com.billing.entity.Company;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        variables.put("Current_Date", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        return variables;
    }

    public String render(String template, Company company, Map<String, Object> inputVariables) {
        String rendered = template == null ? "" : template;
        Map<String, Object> variables = baseVariables(company);
        if (inputVariables != null) {
            variables.putAll(inputVariables);
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            rendered = rendered.replace(placeholder, entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return rendered;
    }
}
