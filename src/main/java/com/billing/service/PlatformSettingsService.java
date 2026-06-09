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
                        .platformName("")
                        .platformLogo(null)
                        .platformTagline(null)
                        .build());
    }

    private PlatformSettingsResponse toResponse(PlatformSetting setting) {
        return PlatformSettingsResponse.builder()
                .platformName(removeLegacyDefault(setting.getPlatformName(), "BizPulse Technologies"))
                .platformLogo(setting.getPlatformLogo())
                .platformTagline(removeLegacyDefault(setting.getPlatformTagline(), "Business Management Platform"))
                .build();
    }

    private String removeLegacyDefault(String value, String legacyDefault) {
        if (value == null) {
            return null;
        }
        return value.trim().equalsIgnoreCase(legacyDefault) ? "" : value;
    }
}
