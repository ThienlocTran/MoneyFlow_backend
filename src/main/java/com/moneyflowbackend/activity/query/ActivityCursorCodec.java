package com.moneyflowbackend.activity.query;

import com.moneyflowbackend.activity.domain.ActivitySource;
import com.moneyflowbackend.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActivityCursorCodec {
    private static final int VERSION = 1;

    public String encode(ActivityCursor cursor) {
        String payload = "v=" + VERSION + "\n"
                + "occurredAt=" + cursor.occurredAt() + "\n"
                + "source=" + cursor.source().name() + "\n"
                + "stableId=" + cursor.stableId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public ActivityCursor decode(String value) {
        if (value == null || value.isBlank()) {
            throw invalidCursor();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            Map<String, String> payload = parsePayload(new String(decoded, StandardCharsets.UTF_8));
            requireVersion(payload.get("v"));
            Instant occurredAt = Instant.parse(requireString(payload, "occurredAt"));
            ActivitySource source = ActivitySource.valueOf(requireString(payload, "source"));
            String stableId = requireString(payload, "stableId");
            validateStableId(source, stableId);
            return new ActivityCursor(occurredAt, source, stableId);
        } catch (Exception ex) {
            throw invalidCursor();
        }
    }

    private Map<String, String> parsePayload(String value) {
        Map<String, String> payload = new HashMap<>();
        for (String line : value.split("\n")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw invalidCursor();
            }
            payload.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return payload;
    }

    private void requireVersion(String value) {
        if (!Integer.toString(VERSION).equals(value)) {
            throw invalidCursor();
        }
    }

    private String requireString(Map<String, String> payload, String key) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            throw invalidCursor();
        }
        return value;
    }

    private void validateStableId(ActivitySource source, String stableId) {
        String prefix = source.name() + ":";
        if (!stableId.startsWith(prefix)) {
            throw invalidCursor();
        }
        UUID.fromString(stableId.substring(prefix.length()));
    }

    private BusinessException invalidCursor() {
        return new BusinessException("INVALID_ACTIVITY_CURSOR", "Invalid activity cursor");
    }
}
