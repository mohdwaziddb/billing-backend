package com.billing.ai.context;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class AiUserContext {
    private Long companyId;
    private String companyName;
    private boolean companyActive;
    private boolean chatbotEnabled;
    private Long userId;
    private String username;
    private String email;
    private String role;
    private boolean userActive;
    private Set<String> permissions;

    public boolean hasPermission(String menuCode, String actionCode) {
        return permissions != null && permissions.contains((menuCode + ":" + actionCode).toUpperCase());
    }
}
