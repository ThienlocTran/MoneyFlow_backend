package com.moneyflowbackend.voice.session;

import com.moneyflowbackend.voice.command.VoiceCommandMode;
import com.moneyflowbackend.voice.dto.VoiceQueryResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VoiceSessionDetailResponse {
    private UUID sessionId;
    private UUID voiceRecordId;
    private VoiceSessionSourceType sourceType;
    private VoiceSessionStatus status;
    private VoiceSessionAudioStatus audioStatus;
    private VoiceSessionAsrStatus asrStatus;
    private VoiceSessionCommandStatus commandStatus;
    private VoiceSessionConfirmStatus confirmStatus;
    private String originalMimeType;
    private String storageMimeType;
    private Long durationMs;
    private Long sizeBytes;
    private boolean playbackAvailable;
    private String transcript;
    private String normalizedTranscript;
    private BigDecimal transcriptConfidence;
    private String asrProvider;
    private String asrModel;
    private String asrLanguage;
    private List<VoiceSessionWarningResponse> asrWarnings;
    private VoiceSessionAsrResponse asr;
    private List<VoiceSessionWarningResponse> commandWarnings;
    private VoiceCommandMode mode;
    private VoiceQueryResponse query;
    private String answerText;
    private List<VoiceSessionDraftResponse> drafts;
    private Instant createdAt;
    private Instant updatedAt;
}
