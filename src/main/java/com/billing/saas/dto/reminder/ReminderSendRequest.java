package com.billing.saas.dto.reminder;

import com.billing.saas.entity.enums.ReminderChannel;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReminderSendRequest {

    @NotNull
    private Long customerId;

    @NotNull
    private ReminderChannel channel;
}
