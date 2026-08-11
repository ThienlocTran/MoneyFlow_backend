package com.moneyflowbackend.voice.session;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voice_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private VoiceSessionSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VoiceSessionStatus status = VoiceSessionStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_status", nullable = false, length = 30)
    @Builder.Default
    private VoiceSessionAudioStatus audioStatus = VoiceSessionAudioStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "asr_status", nullable = false, length = 30)
    @Builder.Default
    private VoiceSessionAsrStatus asrStatus = VoiceSessionAsrStatus.NOT_REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_status", nullable = false, length = 30)
    @Builder.Default
    private VoiceSessionCommandStatus commandStatus = VoiceSessionCommandStatus.NOT_REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirm_status", nullable = false, length = 30)
    @Builder.Default
    private VoiceSessionConfirmStatus confirmStatus = VoiceSessionConfirmStatus.NOT_CONFIRMED;

    @Column(name = "original_mime_type", length = 100)
    private String originalMimeType;

    @Column(name = "storage_mime_type", length = 100)
    private String storageMimeType;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "playback_available", nullable = false)
    @Builder.Default
    private boolean playbackAvailable = false;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "normalized_transcript", columnDefinition = "TEXT")
    private String normalizedTranscript;

    @Column(name = "transcript_confidence", precision = 6, scale = 4)
    private BigDecimal transcriptConfidence;

    @Column(name = "asr_provider", length = 40)
    private String asrProvider;

    @Column(name = "asr_model", length = 120)
    private String asrModel;

    @Column(name = "asr_language", length = 20)
    private String asrLanguage;

    @Column(name = "asr_warnings_json", columnDefinition = "TEXT")
    private String asrWarningsJson;

    @Column(name = "command_warnings_json", columnDefinition = "TEXT")
    private String commandWarningsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
