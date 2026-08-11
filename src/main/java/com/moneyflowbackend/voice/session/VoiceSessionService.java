package com.moneyflowbackend.voice.session;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.transaction.dto.TransactionRequest;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.service.TransactionService;
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
import com.moneyflowbackend.voice.model.VoiceRecord;
import com.moneyflowbackend.voice.model.VoiceRecordStatus;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final VoiceRecordRepository voiceRecordRepository;

    public VoiceSessionService(
            VoiceSessionRepository voiceSessionRepository,
            VoiceSessionDraftRepository voiceSessionDraftRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            VoiceCommandService voiceCommandService,
            VoiceSessionDraftMapper draftMapper,
            VoiceAsrProperties asrProperties,
            VoiceAsrClient asrClient,
            TransactionService transactionService,
            TransactionRepository transactionRepository,
            VoiceRecordRepository voiceRecordRepository) {
        this.voiceSessionRepository = voiceSessionRepository;
        this.voiceSessionDraftRepository = voiceSessionDraftRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.voiceCommandService = voiceCommandService;
        this.draftMapper = draftMapper;
        this.asrProperties = asrProperties;
        this.asrClient = asrClient;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.voiceRecordRepository = voiceRecordRepository;
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
        if (voiceSessionDraftRepository.existsByVoiceSessionIdAndStatus(session.getId(), VoiceSessionDraftStatus.CONFIRMED)) {
            throw new BusinessException("VOICE_SESSION_ALREADY_HAS_CONFIRMED_DRAFTS",
                    VoiceSessionConfirmMessages.message("VOICE_SESSION_ALREADY_HAS_CONFIRMED_DRAFTS"), HttpStatus.CONFLICT);
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
                || (asrProperties.provider() == VoiceAsrProviderType.EXTERNAL_HTTP && !asrProperties.externalServiceConfigured())
                || (asrProperties.provider() == VoiceAsrProviderType.AZURE_SPEECH && !asrProperties.azureSpeechConfigured())) {
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
        ensureSessionVoiceRecord(session, audio);

        VoiceAsrTranscribeResult result = asrClient.transcribe(new VoiceAsrRequest(
                session.getId(),
                bytes,
                audio.getOriginalFilename(),
                contentType(audio),
                language == null || language.isBlank() ? asrProperties.language() : language.trim(),
                returnSegments == null ? asrProperties.returnSegments() : returnSegments,
                normalize));

        applyAsrResult(session, result);
        ensureSessionVoiceRecord(session, null);
        return detail(voiceSessionRepository.save(session));
    }

    @Transactional
    public VoiceSessionConfirmDraftResponse confirmDraft(UUID workspaceId, UUID sessionId, UUID draftId,
                                                         VoiceSessionConfirmDraftRequest req, UUID userId) {
        requireActiveMember(workspaceId, userId);
        VoiceSession session = session(workspaceId, sessionId);
        VoiceSessionDraft draft = draft(session, draftId);
        VoiceSessionConfirmDraftResponse replay = replayIfConfirmed(session, draft);
        if (replay != null) return replay;
        if (draft.getStatus() == VoiceSessionDraftStatus.SKIPPED) {
            return confirmWarning(session, draft, "VOICE_DRAFT_CONFIRM_UNSUPPORTED", VoiceSessionDraftStatus.SKIPPED);
        }
        if (!supportedTransactionDraft(draft)) {
            draft.setStatus(VoiceSessionDraftStatus.UNSUPPORTED);
            draft.setUpdatedAt(Instant.now());
            voiceSessionDraftRepository.save(draft);
            updateConfirmStatus(session);
            return confirmWarning(session, draft, "VOICE_DRAFT_CONFIRM_UNSUPPORTED", VoiceSessionDraftStatus.UNSUPPORTED);
        }
        String validationCode = validationCode(draft, req);
        if (validationCode != null) {
            draft.setStatus(VoiceSessionDraftStatus.NEEDS_REVIEW);
            draft.setWarningsJson(validationCode);
            draft.setUpdatedAt(Instant.now());
            voiceSessionDraftRepository.save(draft);
            updateConfirmStatus(session);
            return confirmWarning(session, draft, validationCode, VoiceSessionDraftStatus.NEEDS_REVIEW);
        }
        TransactionResponse tx = createTransaction(workspaceId, session, draft, req, userId);
        draft.setStatus(VoiceSessionDraftStatus.CONFIRMED);
        draft.setConfirmedEntityType("TRANSACTION");
        draft.setConfirmedEntityId(tx.getId());
        draft.setConfirmedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        voiceSessionDraftRepository.save(draft);
        updateConfirmStatus(session);
        return confirmResponse(session, draft, false, List.of(), tx);
    }

    @Transactional
    public VoiceSessionConfirmEligibleResponse confirmEligible(UUID workspaceId, UUID sessionId,
                                                               VoiceSessionConfirmEligibleRequest req, UUID userId) {
        requireActiveMember(workspaceId, userId);
        VoiceSession session = session(workspaceId, sessionId);
        List<VoiceSessionDraft> drafts = voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(session.getId());
        List<VoiceSessionConfirmDraftResponse> results = drafts.stream()
                .map(draft -> confirmDraft(workspaceId, sessionId, draft.getId(), null, userId))
                .toList();
        int confirmed = (int) results.stream().filter(result -> result.getDraftStatus() == VoiceSessionDraftStatus.CONFIRMED && !result.isIdempotentReplay()).count();
        int skipped = results.size() - confirmed;
        updateConfirmStatus(session);
        List<VoiceSessionWarningResponse> warnings = confirmed == 0
                ? List.of(warningResponse("VOICE_CONFIRM_ELIGIBLE_NONE"))
                : List.of();
        return VoiceSessionConfirmEligibleResponse.builder()
                .sessionId(session.getId())
                .confirmedCount(confirmed)
                .skippedCount(skipped)
                .results(results)
                .sessionStatus(session.getStatus())
                .confirmStatus(session.getConfirmStatus())
                .warnings(warnings)
                .build();
    }

    @Transactional
    public VoiceSessionConfirmDraftResponse skipDraft(UUID workspaceId, UUID sessionId, UUID draftId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        VoiceSession session = session(workspaceId, sessionId);
        VoiceSessionDraft draft = draft(session, draftId);
        if (draft.getStatus() != VoiceSessionDraftStatus.CONFIRMED) {
            draft.setStatus(VoiceSessionDraftStatus.SKIPPED);
            draft.setUpdatedAt(Instant.now());
            voiceSessionDraftRepository.save(draft);
        }
        updateConfirmStatus(session);
        return confirmResponse(session, draft, false, List.of(), null);
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
                .voiceRecordId(sessionVoiceRecordId(session))
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

    private VoiceSessionDraft draft(VoiceSession session, UUID draftId) {
        return voiceSessionDraftRepository.findById(draftId)
                .filter(draft -> draft.getVoiceSession().getId().equals(session.getId()))
                .orElseThrow(() -> new BusinessException("VOICE_DRAFT_NOT_FOUND",
                        VoiceSessionConfirmMessages.message("VOICE_DRAFT_NOT_FOUND"), HttpStatus.NOT_FOUND));
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

    private VoiceSessionConfirmDraftResponse replayIfConfirmed(VoiceSession session, VoiceSessionDraft draft) {
        if (draft.getStatus() != VoiceSessionDraftStatus.CONFIRMED || draft.getConfirmedEntityId() == null) {
            return null;
        }
        TransactionResponse tx = transactionRepository.findByIdAndWorkspaceId(draft.getConfirmedEntityId(), session.getWorkspace().getId())
                .map(transactionService::mapExistingToResponse)
                .orElse(null);
        return confirmResponse(session, draft, true, List.of(warningResponse("VOICE_DRAFT_ALREADY_CONFIRMED")), tx);
    }

    private boolean supportedTransactionDraft(VoiceSessionDraft draft) {
        return "EXPENSE".equals(draft.getType()) || "INCOME".equals(draft.getType());
    }

    private String validationCode(VoiceSessionDraft draft, VoiceSessionConfirmDraftRequest req) {
        if (amount(draft, req) == null || amount(draft, req).signum() <= 0) return "VOICE_DRAFT_AMOUNT_REQUIRED";
        if (draft.isWalletRequired() && walletId(draft, req) == null) return "VOICE_DRAFT_WALLET_REQUIRED";
        if ("EXPENSE".equals(draft.getType()) && categoryId(draft, req) == null) return "VOICE_DRAFT_CATEGORY_REQUIRED";
        return null;
    }

    private TransactionResponse createTransaction(UUID workspaceId, VoiceSession session, VoiceSessionDraft draft,
                                                  VoiceSessionConfirmDraftRequest req, UUID userId) {
        var existing = transactionRepository.findByWorkspaceIdAndVoiceSessionDraftIdAndSourceType(
                workspaceId, draft.getId(), TransactionSourceType.VOICE);
        if (existing.isPresent()) {
            return transactionService.mapExistingToResponse(existing.get());
        }
        TransactionRequest txReq = new TransactionRequest();
        txReq.setType(TransactionType.valueOf(draft.getType()));
        txReq.setStatus(TransactionStatus.POSTED);
        txReq.setAmount(amount(draft, req));
        txReq.setWalletId(walletId(draft, req));
        txReq.setCategoryId(categoryId(draft, req));
        txReq.setTransactionDate(occurredDate(req));
        txReq.setTransactionTime(occurredTime(req));
        txReq.setDescription(note(draft, req));
        txReq.setNote(note(draft, req));
        txReq.setAffectsWalletBalance(!"INCOME".equals(draft.getType()) || walletId(draft, req) != null);
        UUID voiceRecordId = session.getSourceType() == VoiceSessionSourceType.AUDIO
                ? ensureSessionVoiceRecord(session, null).getId()
                : null;
        return transactionService.createWithSource(
                workspaceId,
                txReq,
                userId,
                TransactionSourceType.VOICE,
                sourceText(session, draft),
                voiceRecordId,
                sourceReference(session, draft),
                session.getId(),
                draft.getId());
    }

    private VoiceRecord ensureSessionVoiceRecord(VoiceSession session, MultipartFile audio) {
        String key = voiceRecordKey(session);
        VoiceRecord record = voiceRecordRepository.findVoiceIdempotencyMatch(session.getWorkspace().getId(), session.getUser().getId(), key)
                .orElseGet(() -> VoiceRecord.builder()
                        .workspace(session.getWorkspace())
                        .createdByUser(session.getUser())
                        .idempotencyKey(key)
                        .voiceStatus(VoiceRecordStatus.PARSED)
                        .build());
        record.setOriginalTranscript(session.getTranscript());
        record.setEditedTranscript(session.getNormalizedTranscript());
        if (audio != null) {
            record.setMimeType(contentType(audio));
            record.setFileSizeBytes(audio.getSize());
            record.setDurationSeconds(session.getDurationMs() == null ? null : Math.max(1, (int) Math.ceil(session.getDurationMs() / 1000d)));
        }
        if (record.getVoiceStatus() == VoiceRecordStatus.DRAFT) {
            record.setVoiceStatus(VoiceRecordStatus.PARSED);
        }
        return voiceRecordRepository.save(record);
    }

    private UUID sessionVoiceRecordId(VoiceSession session) {
        if (session.getSourceType() != VoiceSessionSourceType.AUDIO) {
            return null;
        }
        return voiceRecordRepository.findVoiceIdempotencyMatch(session.getWorkspace().getId(), session.getUser().getId(), voiceRecordKey(session))
                .map(VoiceRecord::getId)
                .orElse(null);
    }

    private String voiceRecordKey(VoiceSession session) {
        return "voice-session:" + session.getId();
    }

    private void updateConfirmStatus(VoiceSession session) {
        List<VoiceSessionDraft> drafts = voiceSessionDraftRepository.findAllByVoiceSessionIdOrderByDraftIndexAsc(session.getId());
        boolean anyConfirmed = drafts.stream().anyMatch(draft -> draft.getStatus() == VoiceSessionDraftStatus.CONFIRMED);
        boolean allClosed = !drafts.isEmpty() && drafts.stream().allMatch(draft ->
                draft.getStatus() == VoiceSessionDraftStatus.CONFIRMED
                        || draft.getStatus() == VoiceSessionDraftStatus.SKIPPED
                        || draft.getStatus() == VoiceSessionDraftStatus.UNSUPPORTED);
        session.setConfirmStatus(!anyConfirmed ? VoiceSessionConfirmStatus.NOT_CONFIRMED
                : allClosed ? VoiceSessionConfirmStatus.CONFIRMED : VoiceSessionConfirmStatus.PARTIALLY_CONFIRMED);
        session.setStatus(!anyConfirmed ? session.getStatus()
                : allClosed ? VoiceSessionStatus.CONFIRMED : VoiceSessionStatus.PARTIALLY_CONFIRMED);
        session.setUpdatedAt(Instant.now());
        voiceSessionRepository.save(session);
    }

    private VoiceSessionConfirmDraftResponse confirmWarning(VoiceSession session, VoiceSessionDraft draft, String code, VoiceSessionDraftStatus status) {
        return confirmResponse(session, draft, false, List.of(warningResponse(code)), null);
    }

    private VoiceSessionConfirmDraftResponse confirmResponse(VoiceSession session, VoiceSessionDraft draft, boolean replay,
                                                             List<VoiceSessionWarningResponse> warnings, TransactionResponse tx) {
        return VoiceSessionConfirmDraftResponse.builder()
                .sessionId(session.getId())
                .draftId(draft.getId())
                .draftStatus(draft.getStatus())
                .confirmedEntityType(draft.getConfirmedEntityType())
                .confirmedEntityId(draft.getConfirmedEntityId())
                .idempotentReplay(replay)
                .warnings(warnings)
                .transaction(tx)
                .session(VoiceSessionConfirmDraftResponse.SessionSummary.builder()
                        .status(session.getStatus())
                        .confirmStatus(session.getConfirmStatus())
                        .build())
                .build();
    }

    private VoiceSessionWarningResponse warningResponse(String code) {
        return VoiceSessionWarningResponse.builder()
                .code(code)
                .message(VoiceSessionConfirmMessages.message(code))
                .build();
    }

    private java.math.BigDecimal amount(VoiceSessionDraft draft, VoiceSessionConfirmDraftRequest req) {
        return req != null && req.getAmount() != null ? req.getAmount() : draft.getAmount();
    }

    private UUID walletId(VoiceSessionDraft draft, VoiceSessionConfirmDraftRequest req) {
        return req != null && req.getWalletId() != null ? req.getWalletId() : draft.getWalletId();
    }

    private UUID categoryId(VoiceSessionDraft draft, VoiceSessionConfirmDraftRequest req) {
        return req != null && req.getCategoryId() != null ? req.getCategoryId() : draft.getCategoryId();
    }

    private LocalDate occurredDate(VoiceSessionConfirmDraftRequest req) {
        return req == null || req.getOccurredAt() == null ? null : req.getOccurredAt().toLocalDate();
    }

    private LocalTime occurredTime(VoiceSessionConfirmDraftRequest req) {
        return req == null || req.getOccurredAt() == null ? null : req.getOccurredAt().toLocalTime();
    }

    private String note(VoiceSessionDraft draft, VoiceSessionConfirmDraftRequest req) {
        String requested = normalize(req == null ? null : req.getNote());
        return requested == null ? normalize(draft.getSourceText()) : requested;
    }

    private String sourceText(VoiceSession session, VoiceSessionDraft draft) {
        return Objects.requireNonNullElseGet(normalize(draft.getSourceText()), () -> normalize(session.getNormalizedTranscript()));
    }

    private String sourceReference(VoiceSession session, VoiceSessionDraft draft) {
        return "voice-session:" + session.getId() + ":draft:" + draft.getId();
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
            throw new BusinessException("ASR_UNSUPPORTED_AUDIO_FORMAT", VoiceAsrMessages.message("ASR_UNSUPPORTED_AUDIO_FORMAT"), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
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
