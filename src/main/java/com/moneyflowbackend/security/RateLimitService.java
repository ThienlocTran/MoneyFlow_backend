package com.moneyflowbackend.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitService(Clock clock) {
        this.clock = clock;
    }

    public void check(String key, int maxRequests, Duration windowDuration) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                return new Window(1, now.plus(windowDuration));
            }
            return new Window(current.count() + 1, current.expiresAt());
        });

        if (window.count() > maxRequests) {
            long retryAfter = Math.max(1, Duration.between(now, window.expiresAt()).toSeconds());
            throw new RateLimitExceededException(retryAfter);
        }
    }

    int bucketCount() {
        return windows.size();
    }

    private record Window(int count, Instant expiresAt) {
    }
}
