package com.billing.ai.security;

import com.billing.ai.context.AiUserContext;
import com.billing.exception.AiRateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiRateLimiter {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Value("${ai.chat.rate-limit.user-per-minute:20}")
    private int userLimitPerMinute;

    @Value("${ai.chat.rate-limit.company-per-minute:120}")
    private int companyLimitPerMinute;

    public void check(AiUserContext context) {
        long window = Instant.now().getEpochSecond() / 60;
        increment("company:" + context.getCompanyId(), companyLimitPerMinute, window);
        increment("user:" + context.getCompanyId() + ":" + context.getUserId(), userLimitPerMinute, window);
    }

    private void increment(String key, int limit, long window) {
        WindowCounter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || current.window() != window) {
                return new WindowCounter(window, 1);
            }
            return new WindowCounter(window, current.count() + 1);
        });
        if (counter.count() > limit) {
            throw new AiRateLimitException("Chatbot request limit exceeded. Please try again shortly.");
        }
    }

    private record WindowCounter(long window, int count) {
    }
}
