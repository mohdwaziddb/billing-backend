package com.billing.dto.notification;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class NotificationSendRequest {
    private NotificationChannelType channel;
    private Long templateId;
    private List<String> toEmails;
    private List<String> ccEmails;
    private List<String> bccEmails;
    private List<String> mobileNumbers;
    private List<NotificationAttachmentRequest> attachments;
    private Map<String, Object> variables;
}
