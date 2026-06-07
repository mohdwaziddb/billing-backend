package com.billing.service;

import com.billing.dto.platform.PlatformSettingsResponse;
import com.billing.entity.PlatformSetting;
import com.billing.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingRepository platformSettingRepository;

    @Transactional(readOnly = true)
    public PlatformSettingsResponse getSettings() {
        return platformSettingRepository.findTopByOrderByIdAsc()
                .map(this::toResponse)
                .orElseGet(() -> PlatformSettingsResponse.builder()
                        .platformName("BizPulse Technologies")
                        .platformLogo(null)
                        .platformTagline("Business Management Platform")
                        .build());
    }

    private PlatformSettingsResponse toResponse(PlatformSetting setting) {
        return PlatformSettingsResponse.builder()
                .platformName(setting.getPlatformName())
                .platformLogo(setting.getPlatformLogo())
                .platformTagline(setting.getPlatformTagline())
                .build();
    }
}
