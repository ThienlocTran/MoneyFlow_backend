package com.moneyflowbackend.voice.service;

import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.quickentry.dto.QuickEntryConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
import com.moneyflowbackend.quickentry.service.QuickEntryService;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.service.TransactionService;
import com.moneyflowbackend.voice.dto.VoiceReviewConfirmRequest;
import com.moneyflowbackend.voice.dto.VoiceReviewConfirmResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftRequest;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftType;
import com.moneyflowbackend.voice.dto.VoiceReviewParseRequest;
import com.moneyflowbackend.voice.model.VoiceRecord;
import com.moneyflowbackend.voice.model.VoiceRecordStatus;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class VoiceReviewService {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";

    private final QuickEntryService quickEntryService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final VoiceRecordRepository voiceRecordRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final Clock clock;

    public VoiceReviewService(
            QuickEntryService quickEntryService,
            TransactionService transactionService,
            TransactionRepository transactionRepository,
            VoiceRecordRepository voiceRecordRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            CategoryRepository categoryRepository,
            IncomeSourceRepository incomeSourceRepository,
            Clock clock) {
        this.quickEntryService = quickEntryService;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.voiceRecordRepository = voiceRecordRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.clock = clock;
    }

    @Transactional
    public VoiceReviewDraftResponse parse(UUID workspaceId, VoiceReviewParseRequest req, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        String transcript = transcript(req);
        if (transcript == null) {
            throw new BusinessException("VOICE_TRANSCRIPT_REQUIRED", "Voice transcript is required");
        }
        VoiceRecord record = findOrCreateDraftRecord(member.getWorkspace(), userId, req, transcript);
        QuickEntryPreviewResponse preview = quickEntryService.parse(workspaceId, transcript, userId);
        return fromPreview(member.getWorkspace(), record, preview);
    }

    @Transactional
    public VoiceReviewDraftResponse patchDraft(UUID workspaceId, UUID voiceRecordId, VoiceReviewDraftRequest req, UUID userId) {
        Workspace workspace = requireWritableMember(workspaceId, userId).getWorkspace();
        VoiceRecord record = voiceRecord(workspaceId, voiceRecordId);
        String transcript = record.getEditedTranscript() != null ? record.getEditedTranscript() : record.getOriginalTranscript();
        return fromCandidate(workspace, record, transcript, validateDraft(workspaceId, req, false));
    }

    @Transactional
    public VoiceReviewConfirmResponse confirm(UUID workspaceId, UUID voiceRecordId, VoiceReviewConfirmRequest req, UUID userId) {
        Workspace workspace = requireWritableMember(workspaceId, userId).getWorkspace();
        VoiceRecord record = voiceRecord(workspaceId, voiceRecordId);
        var existing = transactionRepository.findByWorkspaceIdAndVoiceRecordIdAndSourceType(workspaceId, voiceRecordId, TransactionSourceType.VOICE);
        if (existing.isPresent()) {
            TransactionResponse tx = transactionService.mapExistingToResponse(existing.get());
            return confirmResponse(record, tx);
        }
        DraftValidation validation = validateDraft(workspaceId, req == null ? null : req.getCandidate(), true);
        QuickEntryConfirmRequest confirm = toQuickEntryConfirm(workspace, validation);
        TransactionResponse tx = transactionService.createWithSource(
                workspaceId,
                toTransactionRequest(confirm),
                userId,
                TransactionSourceType.VOICE,
                record.getEditedTranscript() != null ? record.getEditedTranscript() : record.getOriginalTranscript(),
                voiceRecordId,
                "voice-review:" + voiceRecordId);
        record.setVoiceStatus(VoiceRecordStatus.CONFIRMED);
        voiceRecordRepository.save(record);
        return confirmResponse(record, tx);
    }

    private VoiceRecord findOrCreateDraftRecord(Workspace workspace, UUID userId, VoiceReviewParseRequest req, String transcript) {
        String idempotencyKey = normalize(req == null ? null : req.getIdempotencyKey());
        if (idempotencyKey != null) {
            var existing = voiceRecordRepository.findVoiceIdempotencyMatch(workspace.getId(), userId, idempotencyKey);
            if (existing.isPresent()) {
                VoiceRecord record = existing.get();
                record.setEditedTranscript(transcript);
                return voiceRecordRepository.save(record);
            }
        }
        return voiceRecordRepository.save(VoiceRecord.builder()
                .workspace(workspace)
                .createdByUser(userRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND)))
                .originalTranscript(transcript)
                .editedTranscript(transcript)
                .idempotencyKey(idempotencyKey)
                .mimeType(normalize(req == null ? null : req.getAudioMimeType()))
                .durationSeconds(req == null ? null : req.getDurationSeconds())
                .voiceStatus(VoiceRecordStatus.PARSED)
                .build());
    }

    private VoiceReviewDraftResponse fromPreview(Workspace workspace, VoiceRecord record, QuickEntryPreviewResponse preview) {
        VoiceReviewDraftType draftType = type(preview.getIntentType(), preview.getType());
        List<String> needsFields = fields(preview.getMissingFields());
        if (draftType == VoiceReviewDraftType.INCOME && preview.getWalletId() == null) {
            needsFields = needsFields.stream().filter(field -> !field.equals("walletId")).toList();
        }
        VoiceReviewDraftResponse.Candidate candidate = VoiceReviewDraftResponse.Candidate.builder()
                .type(draftType)
                .amount(preview.getAmount())
                .currency(currency(workspace))
                .occurredAt(occurredAt(workspace, preview.getTransactionDate(), preview.getTransactionTime()))
                .walletId(preview.getWalletId())
                .walletName(preview.getWalletName())
                .categoryId(preview.getCategoryId())
                .categoryName(preview.getCategoryName())
                .incomeSourceId(preview.getIncomeSourceId())
                .incomeSourceName(preview.getIncomeSourceName())
                .note(preview.getDescription())
                .scope(preview.getSpendingScope())
                .affectsWalletBalance(preview.getWalletId() != null
                        && (preview.getType() == TransactionType.EXPENSE || preview.getType() == TransactionType.INCOME || preview.getType() == TransactionType.TRANSFER))
                .needsFields(needsFields)
                .build();
        return VoiceReviewDraftResponse.builder()
                .voiceRecordId(record.getId())
                .transcript(preview.getRawInput())
                .status("NEEDS_REVIEW")
                .confidence(confidence(preview.getConfidence()))
                .candidate(candidate)
                .warnings(preview.getWarnings() == null ? List.of() : preview.getWarnings())
                .suggestions(suggestions(candidate.getNeedsFields()))
                .audioStatus(audioStatus(record))
                .build();
    }

    private VoiceReviewDraftResponse fromCandidate(Workspace workspace, VoiceRecord record, String transcript, DraftValidation validation) {
        return VoiceReviewDraftResponse.builder()
                .voiceRecordId(record.getId())
                .transcript(transcript)
                .status("NEEDS_REVIEW")
                .confidence(validation.needsFields().isEmpty() ? "HIGH" : "LOW")
                .candidate(validation.candidate())
                .warnings(validation.warnings())
                .suggestions(suggestions(validation.candidate().getNeedsFields()))
                .audioStatus(audioStatus(record))
                .build();
    }

    private DraftValidation validateDraft(UUID workspaceId, VoiceReviewDraftRequest req, boolean committing) {
        if (req == null) {
            throw incomplete("candidate");
        }
        List<String> needs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (req.getType() == null) {
            needs.add("type");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            needs.add("amount");
        }
        Wallet wallet = null;
        Category category = null;
        IncomeSource source = null;
        if (req.getWalletId() != null) {
            wallet = walletRepository.findByIdAndWorkspaceId(req.getWalletId(), workspaceId)
                    .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found", HttpStatus.NOT_FOUND));
            if (!wallet.isActive()) {
                throw new BusinessException("WALLET_INACTIVE", "Wallet is inactive");
            }
        }
        if (req.getCategoryId() != null) {
            category = categoryRepository.findByIdAndWorkspaceId(req.getCategoryId(), workspaceId)
                    .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
            if (!category.isActive()) {
                throw new BusinessException("CATEGORY_INACTIVE", "Category is inactive");
            }
            if (category.isArchived()) {
                throw new BusinessException("CATEGORY_ARCHIVED", "Category is archived");
            }
        }
        if (req.getIncomeSourceId() != null) {
            source = incomeSourceRepository.findByIdAndWorkspaceId(req.getIncomeSourceId(), workspaceId)
                    .orElseThrow(() -> new BusinessException("INCOME_SOURCE_NOT_FOUND", "Income source not found", HttpStatus.NOT_FOUND));
            if (source.getStatus() != IncomeSourceStatus.ACTIVE) {
                throw new BusinessException("INCOME_SOURCE_ARCHIVED", "Income source is archived", HttpStatus.CONFLICT);
            }
        }
        VoiceReviewDraftType type = req.getType() == null ? VoiceReviewDraftType.UNKNOWN : req.getType();
        switch (type) {
            case EXPENSE -> {
                if (wallet == null) needs.add("walletId");
                if (category == null) needs.add("categoryId");
                if (source != null) throw new BusinessException("INVALID_INCOME_SOURCE_LINK", "Expense cannot use incomeSourceId");
            }
            case INCOME -> {
                if (source == null) needs.add("incomeSourceId");
                if (category != null) throw new BusinessException("CATEGORY_NOT_ALLOWED", "Income draft cannot use categoryId");
                if (wallet == null) warnings.add("INCOME_WALLET_NOT_SELECTED");
            }
            case TRANSFER -> {
                warnings.add("VOICE_TRANSFER_CONFIRM_UNSUPPORTED");
                if (committing) throw incomplete("transfer");
            }
            case DEBT -> {
                warnings.add("VOICE_DEBT_CONFIRM_UNSUPPORTED");
                if (committing) throw incomplete("debt");
            }
            case SAVINGS -> {
                warnings.add("VOICE_SAVINGS_CONFIRM_UNSUPPORTED");
                if (committing) throw incomplete("savings");
            }
            case UNKNOWN -> {
                warnings.add("VOICE_UNKNOWN_CONFIRM_UNSUPPORTED");
                if (committing) throw incomplete("type");
            }
        }
        if (committing && (!needs.isEmpty() || (type == VoiceReviewDraftType.INCOME && wallet == null))) {
            throw incomplete(String.join(",", needs.isEmpty() ? List.of("walletId") : needs));
        }
        VoiceReviewDraftResponse.Candidate candidate = VoiceReviewDraftResponse.Candidate.builder()
                .type(type)
                .amount(req.getAmount())
                .currency("VND")
                .occurredAt(req.getOccurredAt())
                .walletId(wallet == null ? null : wallet.getId())
                .walletName(wallet == null ? null : wallet.getName())
                .categoryId(category == null ? null : category.getId())
                .categoryName(category == null ? null : category.getName())
                .incomeSourceId(source == null ? null : source.getId())
                .incomeSourceName(source == null ? null : source.getName())
                .note(normalize(req.getNote()))
                .scope(req.getScope())
                .affectsWalletBalance(wallet != null && (type == VoiceReviewDraftType.EXPENSE || type == VoiceReviewDraftType.INCOME))
                .needsFields(needs.stream().distinct().toList())
                .build();
        return new DraftValidation(candidate, warnings);
    }

    private QuickEntryConfirmRequest toQuickEntryConfirm(Workspace workspace, DraftValidation validation) {
        VoiceReviewDraftResponse.Candidate candidate = validation.candidate();
        QuickEntryConfirmRequest req = new QuickEntryConfirmRequest();
        req.setType(candidate.getType() == VoiceReviewDraftType.INCOME ? TransactionType.INCOME : TransactionType.EXPENSE);
        req.setStatus(TransactionStatus.POSTED);
        req.setAmount(candidate.getAmount());
        req.setWalletId(candidate.getWalletId());
        req.setCategoryId(candidate.getCategoryId());
        req.setIncomeSourceId(candidate.getIncomeSourceId());
        req.setTransactionDate(candidate.getOccurredAt() == null ? today(workspace) : candidate.getOccurredAt().toLocalDate());
        req.setTransactionTime(candidate.getOccurredAt() == null ? null : candidate.getOccurredAt().toLocalTime());
        req.setDescription(candidate.getNote());
        req.setNote(candidate.getNote());
        if (candidate.getScope() != null) {
            req.setSpendingScope(candidate.getScope());
        }
        return req;
    }

    private com.moneyflowbackend.transaction.dto.TransactionRequest toTransactionRequest(QuickEntryConfirmRequest req) {
        com.moneyflowbackend.transaction.dto.TransactionRequest txReq = new com.moneyflowbackend.transaction.dto.TransactionRequest();
        txReq.setType(req.getType());
        txReq.setStatus(req.getStatus());
        txReq.setAmount(req.getAmount());
        txReq.setWalletId(req.getWalletId());
        txReq.setCategoryId(req.getCategoryId());
        txReq.setIncomeSourceId(req.getIncomeSourceId());
        txReq.setTransactionDate(req.getTransactionDate());
        txReq.setTransactionTime(req.getTransactionTime());
        txReq.setDescription(req.getDescription());
        txReq.setNote(req.getNote());
        if (req.hasSpendingScope()) {
            txReq.setSpendingScope(req.getSpendingScope());
        }
        return txReq;
    }

    private VoiceReviewConfirmResponse confirmResponse(VoiceRecord record, TransactionResponse tx) {
        return VoiceReviewConfirmResponse.builder()
                .transactionId(tx.getId())
                .voiceRecordId(record.getId())
                .status(tx.getStatus())
                .audioStatus(audioStatus(record))
                .transaction(tx)
                .build();
    }

    private VoiceReviewDraftType type(VoiceIntentType intentType, TransactionType transactionType) {
        if (transactionType == TransactionType.EXPENSE) return VoiceReviewDraftType.EXPENSE;
        if (transactionType == TransactionType.INCOME) return VoiceReviewDraftType.INCOME;
        if (transactionType == TransactionType.TRANSFER) return VoiceReviewDraftType.TRANSFER;
        if (intentType == null) return VoiceReviewDraftType.UNKNOWN;
        return switch (intentType) {
            case DEBT_CREATE, DEBT_CREATE_PAYABLE, DEBT_CREATE_RECEIVABLE, DEBT_PAYMENT, LOAN_DISBURSEMENT, LOAN_COLLECTION, PAYABLE_REPAYMENT, INTEREST_EXPENSE -> VoiceReviewDraftType.DEBT;
            case SAVINGS_GOAL_CONTRIBUTION, SINKING_FUND_CONTRIBUTION, EMERGENCY_FUND_CONTRIBUTION -> VoiceReviewDraftType.SAVINGS;
            default -> VoiceReviewDraftType.UNKNOWN;
        };
    }

    private List<String> fields(List<String> fields) {
        if (fields == null) return List.of();
        return fields.stream().map(field -> switch (field) {
            case "AMOUNT" -> "amount";
            case "DATE" -> "occurredAt";
            case "TYPE" -> "type";
            case "CATEGORY" -> "categoryId";
            default -> field;
        }).distinct().toList();
    }

    private List<VoiceReviewDraftResponse.Suggestion> suggestions(List<String> fields) {
        if (fields == null) return List.of();
        return fields.stream()
                .map(field -> VoiceReviewDraftResponse.Suggestion.builder()
                        .field(field)
                        .reason(reason(field))
                        .message(message(field))
                        .build())
                .toList();
    }

    private String reason(String field) {
        return "MISSING_" + field.replace("Id", "").toUpperCase(Locale.ROOT);
    }

    private String message(String field) {
        return switch (field) {
            case "walletId" -> "Choose a wallet before saving.";
            case "categoryId" -> "Choose a category before saving.";
            case "incomeSourceId" -> "Choose an income source before saving.";
            case "amount" -> "Enter an amount greater than 0.";
            case "type" -> "Choose a supported transaction type.";
            case "occurredAt" -> "Choose when this happened.";
            default -> "Review this field before saving.";
        };
    }

    private String confidence(double confidence) {
        if (confidence >= 0.85) return "HIGH";
        if (confidence >= 0.6) return "MEDIUM";
        return "LOW";
    }

    private OffsetDateTime occurredAt(Workspace workspace, LocalDate date, java.time.LocalTime time) {
        if (date == null) return null;
        return OffsetDateTime.of(date, time == null ? java.time.LocalTime.MIDNIGHT : time, zone(workspace).getRules().getOffset(clock.instant()));
    }

    private ZoneId zone(Workspace workspace) {
        try {
            return ZoneId.of(workspace.getTimezone() == null ? FALLBACK_ZONE : workspace.getTimezone());
        } catch (DateTimeException ex) {
            return ZoneId.of(FALLBACK_ZONE);
        }
    }

    private LocalDate today(Workspace workspace) {
        return LocalDate.now(clock.withZone(zone(workspace)));
    }

    private String audioStatus(VoiceRecord record) {
        if (record == null || record.getVoiceStatus() == null) return null;
        boolean hasAudio = record.getAudioStorageKey() != null || record.getStorageKey() != null || record.getStoragePublicId() != null;
        if (hasAudio) return "AUDIO_STORED";
        if (record.getVoiceStatus() == VoiceRecordStatus.STORAGE_FAILED || record.getVoiceStatus() == VoiceRecordStatus.FAILED) return "AUDIO_UPLOAD_FAILED";
        if (record.getVoiceStatus() == VoiceRecordStatus.AUDIO_DELETED || record.getVoiceStatus() == VoiceRecordStatus.DELETED) return "AUDIO_DELETED";
        return "AUDIO_NOT_UPLOADED";
    }

    private String currency(Workspace workspace) {
        return workspace.getCurrency() == null ? "VND" : workspace.getCurrency().trim().toUpperCase(Locale.ROOT);
    }

    private String transcript(VoiceReviewParseRequest req) {
        if (req == null) return null;
        String value = normalize(req.getTranscript());
        if (value == null) value = normalize(req.getText());
        if (value == null) value = normalize(req.getRawInput());
        return value;
    }

    private VoiceRecord voiceRecord(UUID workspaceId, UUID voiceRecordId) {
        return voiceRecordRepository.findByIdAndWorkspaceId(voiceRecordId, workspaceId)
                .orElseThrow(() -> new BusinessException("VOICE_RECORD_NOT_FOUND", "Voice record not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        findWorkspace(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify voice review drafts", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private Workspace findWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
    }

    private BusinessException incomplete(String fields) {
        return new BusinessException("VOICE_DRAFT_INCOMPLETE", "Voice draft is incomplete: " + fields);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record DraftValidation(VoiceReviewDraftResponse.Candidate candidate, List<String> warnings) {
        List<String> needsFields() {
            return candidate.getNeedsFields();
        }
    }
}
