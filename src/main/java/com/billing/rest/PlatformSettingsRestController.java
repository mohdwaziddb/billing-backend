package com.billing.rest;

import com.billing.service.PlatformSettingsService;
import com.billing.util.GeneralResponse;
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

    @GetMapping
    public ResponseEntity<?> settings() {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", platformSettingsService.getSettings()), HttpStatus.OK);
    }
}
