package com.billing.dto.notification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationAttachmentRequest {
    private String fileName;
    private String contentType;
    private String base64Content;
}
