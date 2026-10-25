package com.billing.dto.user;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserSessionResponse {
    private Long id;
    private String deviceName;
    private String deviceType;
    private String browser;
    private String os;
    private String ipAddress;
    private LocalDateTime loginAt;
    private LocalDateTime lastActiveAt;
    private String status;
    private LocalDateTime endedAt;
    private String endedReason;
    /** Ye wahi device hai jisse list maangi gayi. */
    private boolean current;
}
