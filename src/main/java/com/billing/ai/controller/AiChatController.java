package com.billing.ai.controller;

import com.billing.ai.dto.AiCancelRequest;
import com.billing.ai.dto.AiChatRequest;
import com.billing.ai.dto.AiChatResponse;
import com.billing.ai.dto.AiConfirmRequest;
import com.billing.ai.service.AiChatService;
import com.billing.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(Authentication authentication,
                                                            @Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI response generated successfully",
                aiChatService.chat(authentication, request)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<AiChatResponse>> confirm(Authentication authentication,
                                                               @Valid @RequestBody AiConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI action processed successfully",
                aiChatService.confirm(authentication, request)));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<AiChatResponse>> cancel(Authentication authentication,
                                                              @Valid @RequestBody AiCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI draft cancelled successfully",
                aiChatService.cancel(authentication, request)));
    }
}
