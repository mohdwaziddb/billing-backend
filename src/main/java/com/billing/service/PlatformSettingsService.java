package com.billing.service;

import com.billing.dto.platform.PlatformSettingsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    // Platform branding is global: served from the common catalog
    // (billing_common.platform_settings), works on any domain without a tenant pool.
    @Qualifier("billingCommonJdbcTemplate")
    private final JdbcTemplate billingCommonJdbcTemplate;

    @Transactional(readOnly = true)
    public PlatformSettingsResponse getSettings() {
        Map<String, Object> row = loadRow();
        if (row == null) {
            return PlatformSettingsResponse.builder()
                    .platformName("")
                    .platformLogo(null)
                    .platformTagline(null)
                    .build();
        }
        return toResponse(row);
    }

    private Map<String, Object> loadRow() {
        try {
            return billingCommonJdbcTemplate.queryForMap(
                    "SELECT platform_name, platform_logo, platform_tagline FROM billing_common.platform_settings ORDER BY id ASC LIMIT 1");
        } catch (Exception e) {
            return null;
        }
    }

    private PlatformSettingsResponse toResponse(Map<String, Object> row) {
        return PlatformSettingsResponse.builder()
                .platformName(removeLegacyDefault(str(row.get("platform_name")), "BizPulse Technologies"))
                .platformLogo(str(row.get("platform_logo")))
                .platformTagline(removeLegacyDefault(str(row.get("platform_tagline")), "Business Management Platform"))
                .build();
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private String removeLegacyDefault(String value, String legacyDefault) {
        if (value == null) {
            return null;
        }
        return value.trim().equalsIgnoreCase(legacyDefault) ? "" : value;
    }
}
