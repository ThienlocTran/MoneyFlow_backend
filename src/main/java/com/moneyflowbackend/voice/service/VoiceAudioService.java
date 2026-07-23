package com.moneyflowbackend.voice.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.dto.VoiceAudioPlaybackResponse;
import com.moneyflowbackend.voice.dto.VoiceAudioStorageStatusResponse;
import com.moneyflowbackend.voice.dto.VoiceAudioUploadResponse;
import com.moneyflowbackend.voice.model.VoiceRecord;
import com.moneyflowbackend.voice.model.VoiceRecordStatus;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.voice.storage.StoredVoiceAudio;
import com.moneyflowbackend.voice.storage.VoiceAudioPlayback;
import com.moneyflowbackend.voice.storage.VoiceAudioStorageService;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class VoiceAudioService {
    private static final Logger log = LoggerFactory.getLogger(VoiceAudioService.class);

    private final VoiceRecordRepository voiceRecordRepository;
    private final TransactionRepository transactionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final VoiceAudioStorageService storageService;
    private final Clock clock;
    private final long maxSizeBytes;
    private final int retentionDays;
    private final Set<String> allowedMimeTypes;

    public VoiceAudioService(
            VoiceRecordRepository voiceRecordRepository,
            TransactionRepository transactionRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            VoiceAudioStorageService storageService,
            Clock clock,
            @Value("${VOICE_AUDIO_MAX_BYTES:${MONEYFLOW_AUDIO_MAX_BYTES:10485760}}") long maxSizeBytes,
            @Value("${VOICE_AUDIO_ALLOWED_MIME_TYPES:${MONEYFLOW_AUDIO_ALLOWED_TYPES:audio/webm,audio/mp4,audio/mpeg,audio/wav}}") String allowedTypes,
            @Value("${VOICE_AUDIO_RETENTION_DAYS:30}") int retentionDays) {
        this.voiceRecordRepository = voiceRecordRepository;
        this.transactionRepository = transactionRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.storageService = storageService;
        this.clock = clock;
        this.maxSizeBytes = Math.max(1, maxSizeBytes);
        this.retentionDays = Math.max(1, retentionDays);
        this.allowedMimeTypes = parseAllowedTypes(allowedTypes);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public VoiceAudioUploadResponse uploadAudio(UUID voiceRecordId, MultipartFile file, Integer durationSeconds, UUID userId) {
        VoiceRecord voiceRecord = findVoiceRecord(voiceRecordId);
        requireWritableMember(voiceRecord, userId);
        requireVoiceTransaction(voiceRecord);
        String mimeType = validateFile(file);
        Integer normalizedDuration = validateDuration(durationSeconds);

        voiceRecord.setMimeType(mimeType);
        voiceRecord.setFileSizeBytes(file.getSize());
        voiceRecord.setDurationSeconds(normalizedDuration);
        try {
            StoredVoiceAudio stored = storageService.upload(objectKey(voiceRecord), file);
            voiceRecord.setStorageProvider(stored.provider());
            voiceRecord.setStorageKey(stored.storageKey());
            voiceRecord.setStoragePublicId(stored.storageKey());
            voiceRecord.setAudioUrl(stored.provider() + ":" + stored.audioUrl());
            voiceRecord.setVoiceStatus(VoiceRecordStatus.AUDIO_STORED);
            voiceRecord.setAudioUploadedAt(Instant.now(clock));
            voiceRecord.setAudioDeletedAt(null);
            voiceRecord.setRetentionUntil(LocalDate.now(clock).plusDays(retentionDays));
            return toUploadResponse(voiceRecordRepository.save(voiceRecord));
        } catch (BusinessException ex) {
            voiceRecord.setStorageProvider(null);
            voiceRecord.setStorageKey(null);
            voiceRecord.setStoragePublicId(null);
            voiceRecord.setAudioUrl(null);
            voiceRecord.setVoiceStatus(VoiceRecordStatus.STORAGE_FAILED);
            voiceRecordRepository.save(voiceRecord);
            throw ex;
        } catch (RuntimeException ex) {
            voiceRecord.setStoragePublicId(null);
            voiceRecord.setAudioUrl(null);
            voiceRecord.setVoiceStatus(VoiceRecordStatus.STORAGE_FAILED);
            voiceRecordRepository.save(voiceRecord);
            throw new BusinessException("AUDIO_STORAGE_FAILED", "Voice audio storage failed", HttpStatus.BAD_GATEWAY);
        }
    }

    @Transactional(readOnly = true)
    public VoiceAudioPlaybackResponse playbackUrl(UUID voiceRecordId, UUID userId) {
        VoiceRecord voiceRecord = findVoiceRecord(voiceRecordId);
        requireActiveMember(voiceRecord, userId);
        if (voiceRecord.getStoragePublicId() == null || voiceRecord.getStoragePublicId().isBlank()) {
            throw new BusinessException("AUDIO_NOT_AVAILABLE", "Voice audio is not available", HttpStatus.NOT_FOUND);
        }
        VoiceAudioPlayback playback = storageService.playbackUrl(storageKey(voiceRecord), voiceRecord.getMimeType());
        return VoiceAudioPlaybackResponse.builder()
                .voiceRecordId(voiceRecord.getId())
                .playbackUrl(playback.playbackUrl())
                .expiresAt(playback.expiresAt())
                .mimeType(playback.mimeType())
                .build();
    }

    @Transactional(readOnly = true)
    public VoiceAudioStorageStatusResponse storageStatus(UUID workspaceId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return VoiceAudioStorageStatusResponse.builder()
                .workspaceId(workspaceId)
                .enabled(storageService.isEnabled())
                .provider(storageService.provider())
                .maxBytes(maxSizeBytes)
                .build();
    }

    @Transactional
    public int deleteExpiredVoiceAudio() {
        LocalDate today = LocalDate.now(clock);
        int deleted = 0;
        for (VoiceRecord voiceRecord : voiceRecordRepository.findAllByRetentionUntilBeforeAndStoragePublicIdIsNotNull(today)) {
            if (clearStoredAudio(voiceRecord, true)) {
                deleted += 1;
            }
        }
        return deleted;
    }

    @Transactional
    public VoiceAudioUploadResponse deleteVoiceAudio(UUID voiceRecordId, UUID userId) {
        VoiceRecord voiceRecord = findVoiceRecord(voiceRecordId);
        requireWritableMember(voiceRecord, userId);
        requireVoiceTransaction(voiceRecord);
        clearStoredAudio(voiceRecord, false);
        return toUploadResponse(voiceRecord);
    }

    private boolean clearStoredAudio(VoiceRecord voiceRecord, boolean bestEffort) {
        String storagePublicId = voiceRecord.getStoragePublicId();
        if (storagePublicId != null && !storagePublicId.isBlank()) {
            if (!storageService.isEnabled()) {
                log.warn("Voice audio cleanup skipped: voiceRecordId={}, reason=storage_disabled", voiceRecord.getId());
                return false;
            }
            try {
                storageService.delete(storageKey(voiceRecord));
            } catch (RuntimeException ex) {
                if (!bestEffort) {
                    throw ex;
                }
                log.warn("Voice audio cleanup skipped: voiceRecordId={}, reason=provider_delete_failed", voiceRecord.getId());
                return false;
            }
        }
        voiceRecord.setAudioUrl(null);
        voiceRecord.setStorageProvider(null);
        voiceRecord.setStorageKey(null);
        voiceRecord.setStoragePublicId(null);
        voiceRecord.setMimeType(null);
        voiceRecord.setFileSizeBytes(null);
        voiceRecord.setDurationSeconds(null);
        voiceRecord.setAudioDeletedAt(Instant.now(clock));
        voiceRecord.setVoiceStatus(VoiceRecordStatus.AUDIO_DELETED);
        voiceRecordRepository.save(voiceRecord);
        return true;
    }

    private VoiceRecord findVoiceRecord(UUID voiceRecordId) {
        return voiceRecordRepository.findById(voiceRecordId)
                .orElseThrow(() -> new BusinessException("VOICE_RECORD_NOT_FOUND", "Voice record not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireActiveMember(VoiceRecord voiceRecord, UUID userId) {
        return requireActiveMember(voiceRecord.getWorkspace().getId(), userId);
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private void requireWritableMember(VoiceRecord voiceRecord, UUID userId) {
        WorkspaceMember member = requireActiveMember(voiceRecord, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot upload voice audio", HttpStatus.FORBIDDEN);
        }
    }

    private void requireVoiceTransaction(VoiceRecord voiceRecord) {
        boolean linked = transactionRepository.existsByWorkspaceIdAndVoiceRecordIdAndSourceType(
                voiceRecord.getWorkspace().getId(),
                voiceRecord.getId(),
                TransactionSourceType.VOICE);
        if (!linked) {
            throw new BusinessException("VOICE_RECORD_NOT_LINKED", "Voice record is not linked to a voice transaction");
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException("AUDIO_FILE_EMPTY", "Audio file is empty");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException("AUDIO_FILE_TOO_LARGE", "Audio file is too large");
        }
        String mimeType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        int separator = mimeType.indexOf(';');
        if (separator >= 0) {
            mimeType = mimeType.substring(0, separator).trim();
        }
        if (!allowedMimeTypes.contains(mimeType)) {
            throw new BusinessException("INVALID_AUDIO_MIME_TYPE", "Audio MIME type is not supported");
        }
        return mimeType;
    }

    private Set<String> parseAllowedTypes(String allowedTypes) {
        return Arrays.stream(allowedTypes.split(","))
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .filter(type -> !type.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Integer validateDuration(Integer durationSeconds) {
        if (durationSeconds == null) {
            return null;
        }
        if (durationSeconds < 1 || durationSeconds > 300) {
            throw new BusinessException("INVALID_AUDIO_DURATION", "Audio duration must be between 1 and 300 seconds");
        }
        return durationSeconds;
    }

    private String objectKey(VoiceRecord voiceRecord) {
        return "workspaces/%s/voice/%s".formatted(voiceRecord.getWorkspace().getId(), voiceRecord.getId());
    }

    private String storageKey(VoiceRecord voiceRecord) {
        return voiceRecord.getStorageKey() == null || voiceRecord.getStorageKey().isBlank()
                ? voiceRecord.getStoragePublicId()
                : voiceRecord.getStorageKey();
    }

    private VoiceAudioUploadResponse toUploadResponse(VoiceRecord voiceRecord) {
        return VoiceAudioUploadResponse.builder()
                .voiceRecordId(voiceRecord.getId())
                .voiceAudioAvailable(voiceRecord.getStoragePublicId() != null)
                .voiceAudioStatus(voiceRecord.getVoiceStatus().name())
                .mimeType(voiceRecord.getMimeType())
                .fileSizeBytes(voiceRecord.getFileSizeBytes())
                .durationSeconds(voiceRecord.getDurationSeconds())
                .uploadedAt(voiceRecord.getAudioUploadedAt())
                .retentionUntil(voiceRecord.getRetentionUntil())
                .build();
    }
}
