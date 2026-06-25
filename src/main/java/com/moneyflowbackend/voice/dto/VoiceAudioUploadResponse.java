package com.moneyflowbackend.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceAudioUploadResponse {
    private UUID voiceRecordId;
    private boolean voiceAudioAvailable;
    private String voiceAudioStatus;
    private String mimeType;
    private Long fileSizeBytes;
    private Integer durationSeconds;
    private LocalDate retentionUntil;
}
