package com.billing.ai.service;

import com.billing.ai.dto.AiChartData;
import com.billing.ai.dto.AiChatRequest;
import com.billing.ai.dto.AiChatResponse;
import com.billing.ai.dto.AiTableData;
import com.billing.ai.tool.BillingAiToolService;
import com.billing.exception.ChatbotDisabledException;
import com.billing.service.AccessControlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiService {

    private static final String SYSTEM_PROMPT = loadSystemPrompt();

    private static final Pattern CHART_PATTERN =
            Pattern.compile("CHART_START\\s*(\\{.*?\\})\\s*CHART_END", Pattern.DOTALL);

    private static final Pattern TABLE_PATTERN =
            Pattern.compile("TABLE_START\\s*(.*?)\\s*TABLE_END", Pattern.DOTALL);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;
    private final AccessControlService accessControlService;

    public AiService(ObjectProvider<ChatModel> chatModelProvider, AccessControlService accessControlService,
                     BillingAiToolService billingAiToolService) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel)
                .defaultTools(MethodToolCallbackProvider.builder().toolObjects(billingAiToolService).build())
                .build();
        this.accessControlService = accessControlService;
    }

    public AiChatResponse chat(AiChatRequest request) {
        if (chatClient == null) {
            return AiChatResponse.builder()
                    .reply("AI is not configured. Please configure the AI provider (base URL, model and API key) in application.properties and restart.")
                    .build();
        }
        var company = accessControlService.getCurrentCompany();
        if (company != null && !company.isChatbotEnabled()) {
            throw new ChatbotDisabledException("AI assistant is disabled for this company. Please contact the platform administrator.");
        }
        try {
            List<Message> historyMessages = toMessages(request.getHistory());
            String rawContent = chatClient.prompt()
                    .system(SYSTEM_PROMPT + "\n\nCurrent date: " + LocalDate.now() + " (use this as today's date for all date-related questions).")
                    .messages(historyMessages)
                    .user(request.getMessage())
                    .call()
                    .content();
            String cleaned = stripThinking(rawContent);
            return AiChatResponse.builder()
                    .reply(cleaned)
                    .chart(parseChart(rawContent))
                    .table(parseTable(rawContent))
                    .build();
        } catch (Exception ex) {
            log.error("AI chat request failed", ex);
            return AiChatResponse.builder()
                    .reply("Sorry, I could not generate a response right now. Please try again.")
                    .build();
        }
    }

    private static String stripThinking(String content) {
        if (content == null) {
            return "";
        }
        String stripped = content
                .replaceAll("(?is)<thinking>.*?</thinking>", "")
                .replaceAll("(?is)<reasoning>.*?</reasoning>", "")
                .replaceAll("(?is)^\\s*thinking\\s*\\r?\\n.*?\\r?\\n\\s*response\\s*\\r?\\n", "")
                .replaceAll("(?is)^\\s*reasoning\\s*\\r?\\n.*?\\r?\\n\\s*response\\s*\\r?\\n", "")
                .replaceAll("(?is)^\\s*thinking\\s*\\r?\\n?.*?\\r?\\n?\\s*response\\s*\\r?\\n?", "")
                .replaceAll("(?is)CHART_START\\s*\\{.*?\\}\\s*CHART_END", "")
                .replaceAll("(?is)TABLE_START.*?TABLE_END", "");
        return stripped.trim();
    }

    private AiChartData parseChart(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = CHART_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        try {
            return objectMapper.readValue(matcher.group(1), AiChartData.class);
        } catch (Exception ex) {
            log.warn("Failed to parse AI chart data: {}", ex.getMessage());
            return null;
        }
    }

    private AiTableData parseTable(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = TABLE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String html = matcher.group(1).trim();
        if (html.isEmpty()) {
            return null;
        }
        AiTableData table = new AiTableData();
        table.setHtml(html);
        return table;
    }

    private List<Message> toMessages(List<Map<String, String>> history) {
        List<Message> messages = new ArrayList<>();
        if (history == null) {
            return messages;
        }
        for (Map<String, String> entry : history) {
            String role = entry.get("role");
            String content = entry.get("content");
            if (content == null || content.isBlank()) {
                continue;
            }
            if ("user".equalsIgnoreCase(role)) {
                messages.add(new UserMessage(content));
            } else if ("assistant".equalsIgnoreCase(role)) {
                messages.add(new AssistantMessage(content));
            }
        }
        return messages;
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource("prompts/ai-system-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load AI system prompt", ex);
        }
    }
}