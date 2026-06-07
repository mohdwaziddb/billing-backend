package com.billing.dto.reminder;

import com.billing.entity.enums.ReminderChannel;
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
