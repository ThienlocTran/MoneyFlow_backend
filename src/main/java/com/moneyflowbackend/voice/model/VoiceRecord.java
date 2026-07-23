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

    @Column(name = "audio_storage_provider", length = 50)
    private String audioStorageProvider;

    @Column(name = "audio_storage_key", columnDefinition = "TEXT")
    private String audioStorageKey;

    @Column(name = "audio_mime_type", length = 100)
    private String audioMimeType;

    @Column(name = "audio_size_bytes")
    private Long audioSizeBytes;

    @Column(name = "audio_uploaded_at")
    private Instant audioUploadedAt;

    @Column(name = "audio_deleted_at")
    private Instant audioDeletedAt;

    @Column(name = "audio_duration_ms")
    private Integer audioDurationMs;

    @Column(name = "audio_upload_status", length = 30)
    private String audioUploadStatus;

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
