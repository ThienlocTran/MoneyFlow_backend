package com.moneyflowbackend.voice.session;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.voice.asr.VoiceAsrClient;
import com.moneyflowbackend.voice.asr.VoiceAsrMessages;
import com.moneyflowbackend.voice.asr.VoiceAsrProperties;
import com.moneyflowbackend.voice.asr.VoiceAsrProviderType;
import com.moneyflowbackend.voice.asr.VoiceAsrRequest;
import com.moneyflowbackend.voice.asr.VoiceAsrTranscribeResult;
import com.moneyflowbackend.voice.asr.VoiceAsrWarning;
import com.moneyflowbackend.voice.command.VoiceCommandInterpretRequest;
import com.moneyflowbackend.voice.command.VoiceCommandInterpretResponse;
import com.moneyflowbackend.voice.command.VoiceCommandMode;
import com.moneyflowbackend.voice.command.VoiceCommandService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class VoiceSessionService {
    private final VoiceSessionRepository voiceSessionRepository;
    private final VoiceSessionDraftRepository voiceSessionDraftRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final VoiceCommandService voiceCommandService;
    private final VoiceSessionDraftMapper draftMapper;
    private final VoiceAsrProperties asrProperties;
    private final VoiceAsrClient asrClient;

    public VoiceSessionService(
            VoiceSessionRepository voiceSessionRepository,
            VoiceSessionDraftRepository voiceSessionDraftRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            VoiceCommandService voiceCommandService,
            VoiceSessionDraftMapper draftMapper,
            VoiceAsrProperties asrProperties,
            VoiceAsrClient asrClient) {
        this.voiceSessionRepository = voiceSessionRepository;
        this.voiceSessionDraftRepository = voiceSessionDraftRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.voiceCommandService = voiceCommandService;
        this.draftMapper = draftMapper;
        this.asrProperties = asrProperties;
        this.asrClient = asrClient;
    }

    @Transactional
    public VoiceSessionCreateResponse create(UUID workspaceId, VoiceSessionCreateRequest req, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        VoiceSessionSourceType sourceType = req == null || req.getSourceType() == null ? VoiceSessionSourceType.TEXT : req.getSourceType();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        VoiceSession session = VoiceSession.builder()
                .workspace(member.getWorkspace())
                .user(user)
                .sourceType(sourceType)
                .originalMimeType(metadataString(req, "mimeType"))
                .durationMs(metadataLong(req, "durationMs"))
                .build();
        session = voiceSessionRepository.save(session);
        return VoiceSessionCreateResponse.builder()
                .sessionId(session.getId())
                .sourceType(session.getSourceType())
                .status(session.getStatus())
                .audioStatus(session.getAudioStatus())
                .asrStatus(session.getAsrStatus())
                .commandStatus(session.getCommandStatus())
                .confirmStatus(session.getConfirmStatus())
                .build();
    }

    @Transactional
    public VoiceSessionDetailResponse updateTranscript(UUID workspaceId, UUID sessionId, VoiceSessionTranscriptUpdateRequest req, UUID userId) {
        requireActiveMember(workspaceId, userId);
        VoiceSession session = session(workspaceId, sessionId);
        String transcript = normalize(req == null ? null : req.getTranscript());
        if (transcript == null) {
            throw new BusinessException("VOICE_SESSION_TRANSCRIPT_REQUIRED", "Voice session transcript is required", HttpStatus.BAD_REQUEST);
        }
        session.setTranscript(transcript);
        session.setNormalizedTranscript(transcript);
        session.setUpdatedAt(Instant.now());
        return detail(voiceSessionRepository.save(session));
    }

    @Transactional
    public VoiceSessionDetailResponse interpret(UUID workspaceId, UUID sessionId, VoiceSessionInterpretRequest req, UUID userId) {
        requireActiveMember(workspaceId, userId);
        VoiceSession session = session(workspaceId, sessionId);
        String transcript = normalize(session.getNormalizedTranscript());
        if (transcript == null) {
            throw new BusinessException("VOICE_SESSION_TRANSCRIPT_REQUIRED", "Voice session transcript is required", HttpStatus.BAD_REQUEST);
        }
        VoiceCommandInterpretRequest commandReq = new VoiceCommandInterpretRequest();
        commandReq.setText(transcript);
        commandReq.setSource("VOICE_SESSION_TEXT");
        commandReq.setTimezone(req == null ? null : req.getTimezone());
        commandReq.setNow(req == null ? null : req.getNow());
        VoiceCommandInterpretResponse interpreted = voiceCommandService.interpretSession(workspaceId, commandReq, userId);

        voiceSessionDraftRepository.deleteByVoiceSessionId(session.getId());
        if (interpreted.getDrafts() != null) {
            VoiceSession draftSession = session;
            voiceSessionDraftRepository.saveAll(interpreted.getDrafts().stream()
                    .map(item -> draftMapper.toEntity(draftSession, item))
                    .toList());
        }
        session.setCommandWarningsJson(draftMapper.writeCommandWarnings(interpreted.getWarnings()));
        session.setCommandStatus(commandStatus(interpreted.getMode()));
        session.setStatus(sessionStatus(interpreted.getMode()));
        session.setUpdatedAt(Instant.now());
        session = voiceSessionRepository.save(session);
        return detail(session, interpreted);
    }

    @Transactional
    public VoiceSessionDetailResponse transcribe(UUID workspaceId, UUID sessionId, MultipartFile audio, String language,
                                                 Boolean returnSegments, boolean normalize, Long durationMs, UUID userId) {
        requireActiveMember(workspaceId, userId);
        VoiceSession session = session(workspaceId, sessionId);
        if (asrProperties.provider() == VoiceAsrProviderType.NONE
                || (asrProperties.provider() == VoiceAsrProviderType.EXTERNAL_HTTP && !asrProperties.externalServiceConfigured())) {
            session.setAsrStatus(VoiceSessionAsrStatus.NOT_REQUESTED);
            session.setAsrWarningsJson(draftMapper.writeAsrWarnings(List.of(warning("ASR_NOT_CONFIGURED"))));
            session.setUpdatedAt(Instant.now());
            return detail(voiceSessionRepository.save(session));
        }

        byte[] bytes = audioBytes(audio);
        validateAudio(audio, bytes, durationMs);
        session.setStatus(VoiceSessionStatus.TRANSCRIBING);
        session.setAsrStatus(VoiceSessionAsrStatus.TRANSCRIBING);
        session.setSourceType(VoiceSessionSourceType.AUDIO);
        session.setOriginalMimeType(contentType(audio));
        session.setSizeBytes((long) bytes.length);
        session.setDurationMs(durationMs);
        session.setAudioStatus(VoiceSessionAudioStatus.NONE);
        session.setUpdatedAt(Instant.now());
        voiceSessionRepository.saveAndFlush(session);

        VoiceAsrTranscribeResult result = asrClient.transcribe(new VoiceAsrRequest(
                session.getId(),
                bytes,
                audio.getOriginalFilename(),
                contentType(audio),
                language == null || language.isBlank() ? asrProperties.language() : language.trim(),
                returnSegments == null ? asrProperties.returnSegments() : returnSegments,
                normalize));

        applyAsrResult(session, result);
        return detail(voiceSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public VoiceSessionDetailResponse get(UUID workspaceId, UUID sessionId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return detail(session(workspaceId, sessionId));
    }

    private VoiceSessionDetailResponse detail(VoiceSession session) {
        return detail(session, null);
    }

    private VoiceSessionDetailResponse detail(VoiceSession session, VoiceCommandInterpretResponse interpreted) {
        List<VoiceSessionDraftResponse> drafts = voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(session.getId()).stream()
                .map(draftMapper::toResponse)
                .toList();
        return VoiceSessionDetailResponse.builder()
                .sessionId(session.getId())
                .sourceType(session.getSourceType())
                .status(session.getStatus())
                .audioStatus(session.getAudioStatus())
                .asrStatus(session.getAsrStatus())
                .commandStatus(session.getCommandStatus())
                .confirmStatus(session.getConfirmStatus())
                .originalMimeType(session.getOriginalMimeType())
                .storageMimeType(session.getStorageMimeType())
                .durationMs(session.getDurationMs())
                .sizeBytes(session.getSizeBytes())
                .playbackAvailable(session.isPlaybackAvailable())
                .transcript(session.getTranscript())
                .normalizedTranscript(session.getNormalizedTranscript())
                .transcriptConfidence(session.getTranscriptConfidence())
                .asrProvider(session.getAsrProvider())
                .asrModel(session.getAsrModel())
                .asrLanguage(session.getAsrLanguage())
                .asrWarnings(draftMapper.readWarnings(session.getAsrWarningsJson()))
                .asr(VoiceSessionAsrResponse.builder()
                        .provider(session.getAsrProvider())
                        .model(session.getAsrModel())
                        .language(session.getAsrLanguage())
                        .durationMs(session.getDurationMs())
                        .confidence(session.getTranscriptConfidence())
                        .warnings(draftMapper.readWarnings(session.getAsrWarningsJson()))
                        .build())
                .commandWarnings(draftMapper.readWarnings(session.getCommandWarningsJson()))
                .mode(interpreted == null ? null : interpreted.getMode())
                .query(interpreted == null ? null : interpreted.getQuery())
                .answerText(interpreted == null ? null : interpreted.getAnswerText())
                .drafts(drafts)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private VoiceSession session(UUID workspaceId, UUID sessionId) {
        return voiceSessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId)
                .orElseThrow(() -> new BusinessException("VOICE_SESSION_NOT_FOUND", "Voice session not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private VoiceSessionCommandStatus commandStatus(VoiceCommandMode mode) {
        if (mode == VoiceCommandMode.NEEDS_CLARIFICATION) return VoiceSessionCommandStatus.NEEDS_CLARIFICATION;
        if (mode == VoiceCommandMode.UNSUPPORTED) return VoiceSessionCommandStatus.UNSUPPORTED;
        if (mode == VoiceCommandMode.READ_ONLY_QUERY) return VoiceSessionCommandStatus.INTERPRETED;
        return VoiceSessionCommandStatus.NEEDS_REVIEW;
    }

    private VoiceSessionStatus sessionStatus(VoiceCommandMode mode) {
        if (mode == VoiceCommandMode.TRANSACTION_REVIEW
                || mode == VoiceCommandMode.MULTI_DRAFT_REVIEW
                || mode == VoiceCommandMode.INCOME_FACT_REVIEW
                || mode == VoiceCommandMode.WALLET_SNAPSHOT_REVIEW
                || mode == VoiceCommandMode.DEBT_DRAFT) {
            return VoiceSessionStatus.NEEDS_REVIEW;
        }
        return VoiceSessionStatus.INTERPRETED;
    }

    private String metadataString(VoiceSessionCreateRequest req, String key) {
        if (req == null || req.getClientMetadata() == null) return null;
        Object value = req.getClientMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private Long metadataLong(VoiceSessionCreateRequest req, String key) {
        if (req == null || req.getClientMetadata() == null) return null;
        Object value = req.getClientMetadata().get(key);
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void applyAsrResult(VoiceSession session, VoiceAsrTranscribeResult result) {
        session.setAsrProvider(result.provider().name());
        session.setAsrModel(result.model());
        session.setAsrLanguage(result.language());
        session.setDurationMs(result.durationMs() == null ? session.getDurationMs() : result.durationMs());
        session.setTranscriptConfidence(result.confidence());
        session.setAsrWarningsJson(draftMapper.writeAsrWarnings(result.warnings()));
        session.setUpdatedAt(Instant.now());
        if (result.succeeded()) {
            String normalizedTranscript = normalize(result.normalizedTranscript() == null ? result.transcript() : result.normalizedTranscript());
            if (normalizedTranscript == null) {
                session.setAsrStatus(VoiceSessionAsrStatus.NO_SPEECH);
                session.setStatus(VoiceSessionStatus.FAILED);
                session.setTranscript(null);
                session.setNormalizedTranscript(null);
                session.setAsrWarningsJson(draftMapper.writeAsrWarnings(List.of(warning("ASR_EMPTY_TRANSCRIPT"))));
                return;
            }
            session.setAsrStatus(VoiceSessionAsrStatus.SUCCEEDED);
            session.setStatus(VoiceSessionStatus.TRANSCRIBED);
            session.setTranscript(normalize(result.transcript()));
            session.setNormalizedTranscript(normalizedTranscript);
            session.setCommandStatus(VoiceSessionCommandStatus.NOT_REQUESTED);
            return;
        }
        session.setAsrStatus(result.status());
        session.setStatus(result.status() == VoiceSessionAsrStatus.NOT_REQUESTED ? session.getStatus() : VoiceSessionStatus.FAILED);
        session.setTranscript(null);
        session.setNormalizedTranscript(null);
    }

    private byte[] audioBytes(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException("ASR_AUDIO_REQUIRED", VoiceAsrMessages.message("ASR_AUDIO_REQUIRED"), HttpStatus.BAD_REQUEST);
        }
        try {
            return audio.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("ASR_AUDIO_REQUIRED", VoiceAsrMessages.message("ASR_AUDIO_REQUIRED"), HttpStatus.BAD_REQUEST);
        }
    }

    private void validateAudio(MultipartFile audio, byte[] bytes, Long durationMs) {
        if (bytes.length == 0) {
            throw new BusinessException("ASR_AUDIO_REQUIRED", VoiceAsrMessages.message("ASR_AUDIO_REQUIRED"), HttpStatus.BAD_REQUEST);
        }
        if (bytes.length > asrProperties.maxFileBytes()) {
            throw new BusinessException("ASR_FILE_TOO_LARGE", VoiceAsrMessages.message("ASR_FILE_TOO_LARGE"), HttpStatus.PAYLOAD_TOO_LARGE);
        }
        if (!asrProperties.allowedMimeTypes().contains(contentType(audio))) {
            throw new BusinessException("ASR_UNSUPPORTED_FORMAT", VoiceAsrMessages.message("ASR_UNSUPPORTED_FORMAT"), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (durationMs != null) {
            double seconds = durationMs / 1000d;
            if (seconds < asrProperties.minAudioSeconds()) {
                throw new BusinessException("ASR_AUDIO_TOO_SHORT", VoiceAsrMessages.message("ASR_AUDIO_TOO_SHORT"), HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (seconds > asrProperties.maxAudioSeconds()) {
                throw new BusinessException("ASR_AUDIO_TOO_LONG", VoiceAsrMessages.message("ASR_AUDIO_TOO_LONG"), HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }

    private String contentType(MultipartFile audio) {
        String contentType = audio == null ? null : audio.getContentType();
        if (contentType == null || contentType.isBlank()) return "application/octet-stream";
        return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private VoiceAsrWarning warning(String code) {
        return new VoiceAsrWarning(code, VoiceAsrMessages.message(code));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    }
}
