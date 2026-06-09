package com.billing.dto.email;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationChannelResponse {
    private Long id;
    private String channelName;
    private boolean defaultChannel;
    private boolean active;
}
