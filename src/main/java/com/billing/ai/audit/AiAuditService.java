package com.billing.ai.audit;

import com.billing.ai.context.AiUserContext;
import com.billing.entity.Company;
import com.billing.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAuditService {

    public static final String AI_CHAT = "AI_CHAT";
    public static final String AI_ACTION = "AI_ACTION";

    private final AuditLogService auditLogService;

    public void log(AiUserContext context, String prompt, String detectedIntent, String action, String status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prompt", prompt);
        data.put("detectedIntent", detectedIntent);
        data.put("action", action);
        data.put("status", status);
        auditLogService.logEvent(
                context.getEmail(),
                companyReference(context),
                "AI Assistant",
                "AIInteraction",
                context.getUserId(),
                action,
                data
        );
    }

    private Company companyReference(AiUserContext context) {
        Company company = new Company();
        company.setId(context.getCompanyId());
        return company;
    }
}
