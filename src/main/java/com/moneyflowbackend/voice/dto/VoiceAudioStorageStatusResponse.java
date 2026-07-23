package com.moneyflowbackend.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceAudioStorageStatusResponse {
    private UUID workspaceId;
    private boolean enabled;
    private String provider;
    private long maxBytes;
}
