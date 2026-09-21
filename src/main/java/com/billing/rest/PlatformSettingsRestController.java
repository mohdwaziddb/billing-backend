package com.billing.rest;

import com.billing.service.PlatformSettingsService;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-settings")
@RequiredArgsConstructor
public class PlatformSettingsRestController {

    private final PlatformSettingsService platformSettingsService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    public ResponseEntity<?> settings() {
        try {
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformSettingsService.getSettings()), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

