package com.moneyflowbackend.voice.session;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class VoiceSessionCreateRequest {
    private VoiceSessionSourceType sourceType;
    private String clientTimezone;
    private OffsetDateTime clientStartedAt;
    private Map<String, Object> clientMetadata;
}
