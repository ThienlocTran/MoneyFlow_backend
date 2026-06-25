package com.moneyflowbackend.voice.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.dto.VoiceAudioPlaybackResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VoiceAudioService {
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "audio/webm",
            "audio/mp4",
            "audio/mpeg",
            "audio/wav",
            "audio/ogg");

    private final VoiceRecordRepository voiceRecordRepository;
    private final TransactionRepository transactionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final VoiceAudioStorageService storageService;
    private final Clock clock;
    private final long maxSizeBytes;
    private final int retentionDays;

    public VoiceAudioService(
            VoiceRecordRepository voiceRecordRepository,
            TransactionRepository transactionRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            VoiceAudioStorageService storageService,
            Clock clock,
            @Value("${VOICE_AUDIO_MAX_SIZE_MB:10}") long maxSizeMb,
            @Value("${VOICE_AUDIO_RETENTION_DAYS:30}") int retentionDays) {
        this.voiceRecordRepository = voiceRecordRepository;
        this.transactionRepository = transactionRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.storageService = storageService;
        this.clock = clock;
        this.maxSizeBytes = Math.max(1, maxSizeMb) * 1024 * 1024;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    public VoiceAudioUploadResponse uploadAudio(UUID voiceRecordId, MultipartFile file, Integer durationSeconds, UUID userId) {
        VoiceRecord voiceRecord = findVoiceRecord(voiceRecordId);
        requireWritableMember(voiceRecord, userId);
        requireVoiceTransaction(voiceRecord);
        String mimeType = validateFile(file);
        Integer normalizedDuration = validateDuration(durationSeconds);

        StoredVoiceAudio stored = storageService.upload(objectKey(voiceRecord), file);
        voiceRecord.setStoragePublicId(stored.storagePublicId());
        voiceRecord.setAudioUrl(stored.audioUrl());
        voiceRecord.setMimeType(mimeType);
        voiceRecord.setFileSizeBytes(file.getSize());
        voiceRecord.setDurationSeconds(normalizedDuration);
        voiceRecord.setVoiceStatus(VoiceRecordStatus.AUDIO_STORED);
        voiceRecord.setRetentionUntil(LocalDate.now(clock).plusDays(retentionDays));
        return toUploadResponse(voiceRecordRepository.save(voiceRecord));
    }

    @Transactional(readOnly = true)
    public VoiceAudioPlaybackResponse playbackUrl(UUID voiceRecordId, UUID userId) {
        VoiceRecord voiceRecord = findVoiceRecord(voiceRecordId);
        requireActiveMember(voiceRecord, userId);
        if (voiceRecord.getStoragePublicId() == null || voiceRecord.getStoragePublicId().isBlank()) {
            throw new BusinessException("AUDIO_NOT_AVAILABLE", "Voice audio is not available", HttpStatus.NOT_FOUND);
        }
        VoiceAudioPlayback playback = storageService.playbackUrl(voiceRecord.getStoragePublicId(), voiceRecord.getMimeType());
        return VoiceAudioPlaybackResponse.builder()
                .voiceRecordId(voiceRecord.getId())
                .playbackUrl(playback.playbackUrl())
                .expiresAt(playback.expiresAt())
                .mimeType(playback.mimeType())
                .build();
    }

    @Transactional
    public int deleteExpiredVoiceAudio() {
        LocalDate today = LocalDate.now(clock);
        int deleted = 0;
        for (VoiceRecord voiceRecord : voiceRecordRepository.findAllByRetentionUntilBeforeAndStoragePublicIdIsNotNull(today)) {
            clearStoredAudio(voiceRecord);
            deleted += 1;
        }
        return deleted;
    }

    @Transactional
    public void deleteVoiceAudio(UUID voiceRecordId) {
        clearStoredAudio(findVoiceRecord(voiceRecordId));
    }

    private void clearStoredAudio(VoiceRecord voiceRecord) {
        String storagePublicId = voiceRecord.getStoragePublicId();
        if (storagePublicId != null && !storagePublicId.isBlank()) {
            storageService.delete(storagePublicId);
        }
        voiceRecord.setAudioUrl(null);
        voiceRecord.setStoragePublicId(null);
        voiceRecord.setMimeType(null);
        voiceRecord.setFileSizeBytes(null);
        voiceRecord.setDurationSeconds(null);
        voiceRecord.setVoiceStatus(VoiceRecordStatus.AUDIO_DELETED);
        voiceRecordRepository.save(voiceRecord);
    }

    private VoiceRecord findVoiceRecord(UUID voiceRecordId) {
        return voiceRecordRepository.findById(voiceRecordId)
                .orElseThrow(() -> new BusinessException("VOICE_RECORD_NOT_FOUND", "Voice record not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireActiveMember(VoiceRecord voiceRecord, UUID userId) {
        UUID workspaceId = voiceRecord.getWorkspace().getId();
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
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException("INVALID_AUDIO_MIME_TYPE", "Audio MIME type is not supported");
        }
        return mimeType;
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

    private VoiceAudioUploadResponse toUploadResponse(VoiceRecord voiceRecord) {
        return VoiceAudioUploadResponse.builder()
                .voiceRecordId(voiceRecord.getId())
                .voiceAudioAvailable(voiceRecord.getStoragePublicId() != null)
                .voiceAudioStatus(voiceRecord.getVoiceStatus().name())
                .mimeType(voiceRecord.getMimeType())
                .fileSizeBytes(voiceRecord.getFileSizeBytes())
                .durationSeconds(voiceRecord.getDurationSeconds())
                .retentionUntil(voiceRecord.getRetentionUntil())
                .build();
    }
}
