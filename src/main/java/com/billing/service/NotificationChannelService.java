package com.billing.service;

import com.billing.dto.email.NotificationChannelResponse;
import com.billing.entity.Company;
import com.billing.entity.NotificationChannel;
import com.billing.repository.NotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationChannelService {

    private final NotificationChannelRepository notificationChannelRepository;
    private final AccessControlService accessControlService;

    @Transactional
    public void ensureDefaultEmailChannel(Company company) {
        if (notificationChannelRepository.existsByCompanyAndChannelNameIgnoreCase(company, "Email")) {
            return;
        }
        notificationChannelRepository.save(NotificationChannel.builder()
                .company(company)
                .channelName("Email")
                .defaultChannel(true)
                .active(true)
                .build());
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelResponse> activeChannels(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return notificationChannelRepository.findByCompanyAndActiveTrueOrderByDefaultChannelDescChannelNameAsc(company).stream()
                .map(this::toResponse)
                .toList();
    }

    private NotificationChannelResponse toResponse(NotificationChannel channel) {
        return NotificationChannelResponse.builder()
                .id(channel.getId())
                .channelName(channel.getChannelName())
                .defaultChannel(channel.isDefaultChannel())
                .active(channel.isActive())
                .build();
    }
}
