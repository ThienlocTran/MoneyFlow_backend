package com.moneyflowbackend.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceAudioPlaybackResponse {
    private UUID voiceRecordId;
    private String playbackUrl;
    private Instant expiresAt;
    private String mimeType;
}
