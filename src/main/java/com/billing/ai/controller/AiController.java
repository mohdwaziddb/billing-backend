package com.billing.ai.controller;

import com.billing.ai.dto.AiChatRequest;
import com.billing.ai.dto.AiChatResponse;
import com.billing.ai.service.AiService;
import com.billing.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    // Pehle koi check nahi tha: AI_ASSISTANT menu na ho tab bhi chat chalti thi.
    // Har tool apni alag permission bhi check karta hai (BillingAiToolService).
    @PostMapping
    @com.billing.security.RequiresPermission(menu = "AI_ASSISTANT", action = "VIEW")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(ApiResponse.success("AI response generated successfully", aiService.chat(request)));
    }
}