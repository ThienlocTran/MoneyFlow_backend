package com.moneyflowbackend.voice.model;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "voice_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "storage_public_id", length = 255)
    private String storagePublicId;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "original_transcript", columnDefinition = "TEXT")
    private String originalTranscript;

    @Column(name = "edited_transcript", columnDefinition = "TEXT")
    private String editedTranscript;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "voice_status", nullable = false, length = 20)
    @Builder.Default
    private VoiceRecordStatus voiceStatus = VoiceRecordStatus.DRAFT;

    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
