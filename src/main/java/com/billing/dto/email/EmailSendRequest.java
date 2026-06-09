package com.billing.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class EmailSendRequest {

    @NotNull
    private Long templateId;

    @Email
    @NotBlank
    private String recipientEmail;

    private Map<String, Object> variables;
}
