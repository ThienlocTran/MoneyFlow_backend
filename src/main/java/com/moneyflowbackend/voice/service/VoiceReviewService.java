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
    public VoiceReviewDraftResponse patchDraft(UUID workspaceId, UUID voiceRecordId, String draftId, VoiceReviewDraftRequest req, UUID userId) {
        Workspace workspace = requireWritableMember(workspaceId, userId).getWorkspace();
        VoiceRecord record = voiceRecord(workspaceId, voiceRecordId);
        requireDraft(workspaceId, record, draftId, userId);
        if (req != null) {
            req.setDraftId(draftId);
        }
        String transcript = record.getEditedTranscript() != null ? record.getEditedTranscript() : record.getOriginalTranscript();
        return fromDraftItem(workspace, record, transcript, draftId, validateDraft(workspaceId, req, false));
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

    @Transactional
    public VoiceReviewConfirmResponse confirmDraft(UUID workspaceId, UUID voiceRecordId, String draftId, VoiceReviewConfirmRequest req, UUID userId) {
        Workspace workspace = requireWritableMember(workspaceId, userId).getWorkspace();
        VoiceRecord record = voiceRecord(workspaceId, voiceRecordId);
        String sourceReference = draftSourceReference(voiceRecordId, draftId);
        var existing = transactionRepository.findSourceReferenceMatches(workspaceId, userId, TransactionSourceType.VOICE, sourceReference);
        if (!existing.isEmpty()) {
            return confirmResponse(record, transactionService.mapExistingToResponse(existing.get(0)));
        }
        VoiceReviewDraftRequest candidate = req == null ? null : req.getCandidate();
        if (candidate == null) {
            candidate = toRequest(requireDraft(workspaceId, record, draftId, userId).getCandidate());
        } else {
            candidate.setDraftId(draftId);
        }
        DraftValidation validation = validateDraft(workspaceId, candidate, true);
        TransactionResponse tx = transactionService.createWithSource(
                workspaceId,
                toTransactionRequest(toQuickEntryConfirm(workspace, validation)),
                userId,
                TransactionSourceType.VOICE,
                requireDraft(workspaceId, record, draftId, userId).getSourceText(),
                voiceRecordId,
                sourceReference);
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
        VoiceReviewDraftType draftType = type(preview.getIntentType(), preview.getType(), preview.isAffectsWalletBalance(), preview.getWalletId());
        List<String> needsFields = fields(preview.getMissingFields());
        if (draftType == VoiceReviewDraftType.INCOME && preview.getWalletId() == null) {
            needsFields = needsFields.stream().filter(field -> !field.equals("walletId")).toList();
        }
        if (savingsLike(draftType)) {
            needsFields = savingsNeeds(needsFields);
        }
        if (debtLike(draftType)) {
            needsFields = debtNeeds(draftType, needsFields);
        }
        VoiceReviewDraftResponse.Candidate candidate = VoiceReviewDraftResponse.Candidate.builder()
                .type(draftType)
                .amount(preview.getAmount())
                .currency(currency(workspace))
                .occurredAt(occurredAt(workspace, preview.getTransactionDate(), preview.getTransactionTime()))
                .walletId(preview.getWalletId())
                .walletName(preview.getWalletName())
                .sourceWalletId(preview.getWalletId())
                .sourceWalletName(preview.getWalletName())
                .categoryId(preview.getCategoryId())
                .categoryName(preview.getCategoryName())
                .incomeSourceId(preview.getIncomeSourceId())
                .incomeSourceName(preview.getIncomeSourceName())
                .debtDirection(debtDirection(draftType))
                .counterpartyName(counterpartyName(draftType, preview.getRawInput()))
                .destinationWalletId(preview.getDestinationWalletId())
                .destinationWalletName(preview.getDestinationWalletName())
                .targetFundNameCandidate(savingsTargetCandidate(draftType, preview.getRawInput()))
                .note(preview.getDescription())
                .scope(preview.getSpendingScope())
                .affectsWalletBalance(debtLike(draftType) || (!savingsLike(draftType) && preview.isAffectsWalletBalance()))
                .countsAsExpense(draftType == VoiceReviewDraftType.EXPENSE)
                .countsAsIncome(draftType == VoiceReviewDraftType.INCOME)
                .walletRequired(draftType == VoiceReviewDraftType.EXPENSE || draftType == VoiceReviewDraftType.INCOME || savingsLike(draftType) || debtLike(draftType))
                .categoryRequired(draftType == VoiceReviewDraftType.EXPENSE)
                .needsFields(needsFields)
                .build();
        candidate.setCanConfirm(canConfirm(candidate));
        return VoiceReviewDraftResponse.builder()
                .voiceRecordId(record.getId())
                .transcript(preview.getRawInput())
                .mode(drafts(workspace, preview).size() > 1 ? "MULTI" : "SINGLE")
                .status("NEEDS_REVIEW")
                .confidence(confidence(preview.getConfidence()))
                .candidate(candidate)
                .warnings(topWarnings(workspace, preview))
                .warningDetails(warnings(topWarnings(workspace, preview)))
                .suggestions(suggestions(candidate.getNeedsFields()))
                .drafts(drafts(workspace, preview))
                .audioStatus(audioStatus(record))
                .build();
    }

    private VoiceReviewDraftResponse fromCandidate(Workspace workspace, VoiceRecord record, String transcript, DraftValidation validation) {
        return VoiceReviewDraftResponse.builder()
                .voiceRecordId(record.getId())
                .transcript(transcript)
                .mode("SINGLE")
                .status("NEEDS_REVIEW")
                .confidence(validation.needsFields().isEmpty() ? "HIGH" : "LOW")
                .candidate(validation.candidate())
                .warnings(validation.warnings())
                .warningDetails(warnings(validation.warnings()))
                .suggestions(suggestions(validation.candidate().getNeedsFields()))
                .drafts(List.of(draftItem(normalize(validation.candidate().getNote()), "draft", 0, validation.candidate(), validation.warnings(), validation.needsFields().isEmpty())))
                .audioStatus(audioStatus(record))
                .build();
    }

    private VoiceReviewDraftResponse fromDraftItem(Workspace workspace, VoiceRecord record, String transcript, String draftId, DraftValidation validation) {
        VoiceReviewDraftResponse response = fromCandidate(workspace, record, transcript, validation);
        response.getDrafts().get(0).setDraftId(draftId);
        response.getDrafts().get(0).setCanConfirm(canConfirm(validation.candidate()));
        return response;
    }

    private List<String> topWarnings(Workspace workspace, QuickEntryPreviewResponse preview) {
        List<String> warnings = new ArrayList<>(preview.getWarnings() == null ? List.of() : preview.getWarnings());
        if (drafts(workspace, preview).size() > 1 && !warnings.contains("MULTIPLE_AMOUNTS_DETECTED")) {
            warnings.add("MULTIPLE_AMOUNTS_DETECTED");
        }
        if (drafts(workspace, preview).size() > 1 && !warnings.contains("VOICE_MULTI_INTENT_DETECTED")) {
            warnings.add("VOICE_MULTI_INTENT_DETECTED");
        }
        return warnings;
    }

    private List<VoiceReviewDraftResponse.DraftItem> drafts(Workspace workspace, QuickEntryPreviewResponse preview) {
        List<QuickEntryPreviewResponse.Candidate> candidates = preview.getCandidates() == null ? List.of() : preview.getCandidates();
        if (candidates.isEmpty()) {
            return List.of(draftItem(preview.getRawInput(), preview.getCandidateId(), 0, candidate(workspace, preview), preview.getWarnings(), preview.isReadyToConfirm()));
        }
        List<VoiceReviewDraftResponse.DraftItem> items = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            QuickEntryPreviewResponse.Candidate c = candidates.get(i);
            items.add(draftItem(c.getOriginalText(), c.getCandidateId(), i, candidate(workspace, c), c.getWarnings(), c.isReadyToConfirm()));
        }
        return items;
    }

    private VoiceReviewDraftResponse.DraftItem draftItem(String sourceText, String draftId, int index, VoiceReviewDraftResponse.Candidate candidate, List<String> warningCodes, boolean ready) {
        List<String> codes = new ArrayList<>(warningCodes == null ? List.of() : warningCodes);
        if (candidate.getType() == VoiceReviewDraftType.INCOME && candidate.getWalletId() == null && !codes.contains("INCOME_WALLET_NOT_SELECTED")) {
            codes.add("INCOME_WALLET_NOT_SELECTED");
        }
        if (candidate.getType() == VoiceReviewDraftType.INCOME_FACT && !codes.contains("INCOME_FACT_NO_WALLET_EFFECT")) {
            codes.add("INCOME_FACT_NO_WALLET_EFFECT");
        }
        if (candidate.getType() == VoiceReviewDraftType.WALLET_SNAPSHOT && !codes.contains("WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED")) {
            codes.add("WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED");
        }
        if (savingsLike(candidate.getType())) {
            addSavingsWarnings(codes);
        }
        if (debtLike(candidate.getType())) {
            addDebtWarnings(codes, candidate.getType());
        }
        if (!supported(candidate.getType()) && !codes.contains("DRAFT_UNSUPPORTED_TYPE")) {
            codes.add("DRAFT_UNSUPPORTED_TYPE");
            codes.add("VOICE_SEGMENT_UNSUPPORTED");
        }
        candidate.setCanConfirm(ready && canConfirm(candidate));
        return VoiceReviewDraftResponse.DraftItem.builder()
                .draftId(draftId)
                .index(index)
                .sourceText(sourceText)
                .confidence(ready ? "HIGH" : "LOW")
                .candidate(candidate)
                .warnings(warnings(codes))
                .suggestions(suggestions(candidate.getNeedsFields()))
                .canConfirm(candidate.isCanConfirm())
                .build();
    }

    private VoiceReviewDraftResponse.Candidate candidate(Workspace workspace, QuickEntryPreviewResponse preview) {
        VoiceReviewDraftType draftType = type(preview.getIntentType(), preview.getType(), preview.isAffectsWalletBalance(), preview.getWalletId());
        List<String> needsFields = fields(preview.getMissingFields());
        if (draftType == VoiceReviewDraftType.INCOME && preview.getWalletId() == null) {
            needsFields = needsFields.stream().filter(field -> !field.equals("walletId")).toList();
        }
        if (savingsLike(draftType)) {
            needsFields = savingsNeeds(needsFields);
        }
        if (debtLike(draftType)) {
            needsFields = debtNeeds(draftType, needsFields);
        }
        return VoiceReviewDraftResponse.Candidate.builder()
                .type(draftType)
                .amount(preview.getAmount())
                .currency(currency(workspace))
                .occurredAt(occurredAt(workspace, preview.getTransactionDate(), preview.getTransactionTime()))
                .walletId(preview.getWalletId())
                .walletName(preview.getWalletName())
                .sourceWalletId(preview.getWalletId())
                .sourceWalletName(preview.getWalletName())
                .categoryId(preview.getCategoryId())
                .categoryName(preview.getCategoryName())
                .incomeSourceId(preview.getIncomeSourceId())
                .incomeSourceName(preview.getIncomeSourceName())
                .debtDirection(debtDirection(draftType))
                .counterpartyName(counterpartyName(draftType, preview.getRawInput()))
                .destinationWalletId(preview.getDestinationWalletId())
                .destinationWalletName(preview.getDestinationWalletName())
                .targetFundNameCandidate(savingsTargetCandidate(draftType, preview.getRawInput()))
                .note(preview.getDescription())
                .scope(preview.getSpendingScope())
                .affectsWalletBalance(debtLike(draftType) || (!savingsLike(draftType) && preview.isAffectsWalletBalance()))
                .countsAsExpense(draftType == VoiceReviewDraftType.EXPENSE)
                .countsAsIncome(draftType == VoiceReviewDraftType.INCOME)
                .walletRequired(draftType == VoiceReviewDraftType.EXPENSE || draftType == VoiceReviewDraftType.INCOME || savingsLike(draftType) || debtLike(draftType))
                .categoryRequired(draftType == VoiceReviewDraftType.EXPENSE)
                .canConfirm(false)
                .needsFields(needsFields)
                .build();
    }

    private VoiceReviewDraftResponse.Candidate candidate(Workspace workspace, QuickEntryPreviewResponse.Candidate preview) {
        VoiceReviewDraftType draftType = type(preview.getIntentType(), preview.getType(), preview.isAffectsWalletBalance(), preview.getWalletId());
        List<String> needsFields = fields(preview.getMissingFields());
        if (draftType == VoiceReviewDraftType.INCOME && preview.getWalletId() == null) {
            needsFields = needsFields.stream().filter(field -> !field.equals("walletId")).toList();
        }
        if (savingsLike(draftType)) {
            needsFields = savingsNeeds(needsFields);
        }
        if (debtLike(draftType)) {
            needsFields = debtNeeds(draftType, needsFields);
        }
        return VoiceReviewDraftResponse.Candidate.builder()
                .type(draftType)
                .amount(preview.getAmount())
                .currency(currency(workspace))
                .occurredAt(occurredAt(workspace, preview.getTransactionDate(), preview.getTransactionTime()))
                .walletId(preview.getWalletId())
                .walletName(preview.getWalletName())
                .sourceWalletId(preview.getWalletId())
                .sourceWalletName(preview.getWalletName())
                .categoryId(preview.getCategoryId())
                .categoryName(preview.getCategoryName())
                .incomeSourceId(preview.getIncomeSourceId())
                .incomeSourceName(preview.getIncomeSourceName())
                .debtDirection(debtDirection(draftType))
                .counterpartyName(counterpartyName(draftType, preview.getOriginalText()))
                .destinationWalletId(preview.getDestinationWalletId())
                .destinationWalletName(preview.getDestinationWalletName())
                .targetFundNameCandidate(savingsTargetCandidate(draftType, preview.getOriginalText()))
                .note(preview.getDescription())
                .scope(preview.getSpendingScope())
                .affectsWalletBalance(debtLike(draftType) || (!savingsLike(draftType) && preview.isAffectsWalletBalance()))
                .countsAsExpense(draftType == VoiceReviewDraftType.EXPENSE)
                .countsAsIncome(draftType == VoiceReviewDraftType.INCOME)
                .walletRequired(draftType == VoiceReviewDraftType.EXPENSE || draftType == VoiceReviewDraftType.INCOME || savingsLike(draftType) || debtLike(draftType))
                .categoryRequired(draftType == VoiceReviewDraftType.EXPENSE)
                .canConfirm(false)
                .needsFields(needsFields)
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
            case INCOME_FACT -> {
                if (category != null) throw new BusinessException("CATEGORY_NOT_ALLOWED", "Income draft cannot use categoryId");
                if (wallet != null) throw new BusinessException("WALLET_NOT_ALLOWED", "Income fact must not use walletId");
                warnings.add("INCOME_FACT_NO_WALLET_EFFECT");
            }
            case TRANSFER -> {
                warnings.add("VOICE_TRANSFER_CONFIRM_UNSUPPORTED");
                if (committing) throw incomplete("transfer");
            }
            case WALLET_SNAPSHOT -> {
                if (wallet == null) needs.add("walletId");
                warnings.add(wallet == null ? "WALLET_SNAPSHOT_NO_WALLET" : "WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED");
                if (committing) throw new BusinessException("WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED", "MoneyFlow đã hiểu đây là số dư ví, nhưng luồng lưu snapshot chưa bật.");
            }
            case DEBT -> {
                addDebtWarnings(warnings, type);
                if (committing) throw debtUnsupported();
            }
            case LOAN_DISBURSEMENT, BORROWING_RECEIPT -> {
                needs.add("counterpartyId");
                needs.add(type == VoiceReviewDraftType.LOAN_DISBURSEMENT ? "sourceWalletId" : "destinationWalletId");
                addDebtWarnings(warnings, type);
                if (committing) throw debtUnsupported();
            }
            case LOAN_COLLECTION, BORROWING_REPAYMENT -> {
                needs.add("debtId");
                needs.add(type == VoiceReviewDraftType.LOAN_COLLECTION ? "destinationWalletId" : "sourceWalletId");
                addDebtWarnings(warnings, type);
                if (committing) throw debtUnsupported();
            }
            case SAVINGS -> {
                needs.add("sourceWalletId");
                needs.add("targetFundId");
                addSavingsWarnings(warnings);
                if (committing) throw savingsUnsupported();
            }
            case SAVINGS_ALLOCATION, SINKING_FUND_CONTRIBUTION, EMERGENCY_FUND_CONTRIBUTION -> {
                needs.add("sourceWalletId");
                needs.add("targetFundId");
                addSavingsWarnings(warnings);
                if (committing) throw savingsUnsupported();
            }
            case UNKNOWN -> {
                warnings.add("VOICE_UNKNOWN_CONFIRM_UNSUPPORTED");
                if (committing) throw incomplete("type");
            }
        }
        if (committing && !needs.isEmpty()) {
            throw incomplete(String.join(",", needs));
        }
        VoiceReviewDraftResponse.Candidate candidate = VoiceReviewDraftResponse.Candidate.builder()
                .type(type)
                .amount(req.getAmount())
                .currency("VND")
                .occurredAt(req.getOccurredAt())
                .walletId(wallet == null ? null : wallet.getId())
                .walletName(wallet == null ? null : wallet.getName())
                .sourceWalletId(req.getSourceWalletId())
                .categoryId(category == null ? null : category.getId())
                .categoryName(category == null ? null : category.getName())
                .incomeSourceId(source == null ? null : source.getId())
                .incomeSourceName(source == null ? null : source.getName())
                .debtDirection(req.getDebtDirection() == null ? debtDirection(type) : req.getDebtDirection())
                .counterpartyId(req.getCounterpartyId())
                .counterpartyName(normalize(req.getCounterpartyName()))
                .debtId(req.getDebtId())
                .targetFundId(req.getTargetFundId())
                .destinationWalletId(req.getDestinationWalletId())
                .jarId(req.getJarId())
                .note(normalize(req.getNote()))
                .scope(req.getScope())
                .affectsWalletBalance(debtLike(type) || (wallet != null && (type == VoiceReviewDraftType.EXPENSE || type == VoiceReviewDraftType.INCOME)))
                .countsAsExpense(type == VoiceReviewDraftType.EXPENSE)
                .countsAsIncome(type == VoiceReviewDraftType.INCOME)
                .walletRequired(type == VoiceReviewDraftType.EXPENSE || type == VoiceReviewDraftType.INCOME || savingsLike(type) || debtLike(type))
                .categoryRequired(type == VoiceReviewDraftType.EXPENSE)
                .needsFields(needs.stream().distinct().toList())
                .build();
        candidate.setCanConfirm(canConfirm(candidate));
        return new DraftValidation(candidate, warnings);
    }

    private QuickEntryConfirmRequest toQuickEntryConfirm(Workspace workspace, DraftValidation validation) {
        VoiceReviewDraftResponse.Candidate candidate = validation.candidate();
        QuickEntryConfirmRequest req = new QuickEntryConfirmRequest();
        req.setType(candidate.getType() == VoiceReviewDraftType.INCOME || candidate.getType() == VoiceReviewDraftType.INCOME_FACT ? TransactionType.INCOME : TransactionType.EXPENSE);
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
        txReq.setAffectsWalletBalance(req.getWalletId() != null);
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

    private VoiceReviewDraftType type(VoiceIntentType intentType, TransactionType transactionType, boolean affectsWalletBalance, UUID walletId) {
        if (intentType == VoiceIntentType.WALLET_BALANCE_SNAPSHOT) return VoiceReviewDraftType.WALLET_SNAPSHOT;
        if (transactionType == TransactionType.EXPENSE) return VoiceReviewDraftType.EXPENSE;
        if (transactionType == TransactionType.INCOME) {
            return affectsWalletBalance || walletId != null ? VoiceReviewDraftType.INCOME : VoiceReviewDraftType.INCOME_FACT;
        }
        if (transactionType == TransactionType.TRANSFER) return VoiceReviewDraftType.TRANSFER;
        if (intentType == null) return VoiceReviewDraftType.UNKNOWN;
        return switch (intentType) {
            case LOAN_DISBURSEMENT, DEBT_CREATE_RECEIVABLE -> VoiceReviewDraftType.LOAN_DISBURSEMENT;
            case LOAN_COLLECTION -> VoiceReviewDraftType.LOAN_COLLECTION;
            case BORROWING_RECEIPT, DEBT_CREATE_PAYABLE -> VoiceReviewDraftType.BORROWING_RECEIPT;
            case DEBT_PAYMENT, PAYABLE_REPAYMENT -> VoiceReviewDraftType.BORROWING_REPAYMENT;
            case DEBT_CREATE, INTEREST_EXPENSE -> VoiceReviewDraftType.DEBT;
            case SAVINGS_GOAL_CONTRIBUTION -> VoiceReviewDraftType.SAVINGS_ALLOCATION;
            case SINKING_FUND_CONTRIBUTION -> VoiceReviewDraftType.SINKING_FUND_CONTRIBUTION;
            case EMERGENCY_FUND_CONTRIBUTION -> VoiceReviewDraftType.EMERGENCY_FUND_CONTRIBUTION;
            default -> VoiceReviewDraftType.UNKNOWN;
        };
    }

    private boolean savingsLike(VoiceReviewDraftType type) {
        return type == VoiceReviewDraftType.SAVINGS
                || type == VoiceReviewDraftType.SAVINGS_ALLOCATION
                || type == VoiceReviewDraftType.SINKING_FUND_CONTRIBUTION
                || type == VoiceReviewDraftType.EMERGENCY_FUND_CONTRIBUTION;
    }

    private boolean debtLike(VoiceReviewDraftType type) {
        return type == VoiceReviewDraftType.DEBT
                || type == VoiceReviewDraftType.LOAN_DISBURSEMENT
                || type == VoiceReviewDraftType.LOAN_COLLECTION
                || type == VoiceReviewDraftType.BORROWING_RECEIPT
                || type == VoiceReviewDraftType.BORROWING_REPAYMENT;
    }

    private String debtDirection(VoiceReviewDraftType type) {
        if (type == VoiceReviewDraftType.LOAN_DISBURSEMENT || type == VoiceReviewDraftType.LOAN_COLLECTION) return "RECEIVABLE";
        if (type == VoiceReviewDraftType.BORROWING_RECEIPT || type == VoiceReviewDraftType.BORROWING_REPAYMENT) return "PAYABLE";
        return null;
    }

    private List<String> debtNeeds(VoiceReviewDraftType type, List<String> fields) {
        List<String> needs = new ArrayList<>(fields == null ? List.of() : fields);
        needs.remove("categoryId");
        needs.remove("counterpartyId");
        needs.remove("debtId");
        if (type == VoiceReviewDraftType.LOAN_DISBURSEMENT) {
            needs.add("counterpartyId");
            needs.add("sourceWalletId");
        } else if (type == VoiceReviewDraftType.BORROWING_RECEIPT) {
            needs.add("counterpartyId");
            needs.add("destinationWalletId");
        } else if (type == VoiceReviewDraftType.LOAN_COLLECTION) {
            needs.add("debtId");
            needs.add("destinationWalletId");
        } else if (type == VoiceReviewDraftType.BORROWING_REPAYMENT) {
            needs.add("debtId");
            needs.add("sourceWalletId");
        }
        return needs.stream().distinct().toList();
    }

    private void addDebtWarnings(List<String> codes, VoiceReviewDraftType type) {
        addCode(codes, "DEBT_NOT_EXPENSE");
        addCode(codes, "DEBT_NOT_INCOME");
        if (type == VoiceReviewDraftType.LOAN_COLLECTION || type == VoiceReviewDraftType.BORROWING_REPAYMENT) {
            addCode(codes, "DEBT_NOT_FOUND");
        } else {
            addCode(codes, "DEBT_COUNTERPARTY_REQUIRED");
        }
        if (type != VoiceReviewDraftType.DEBT) {
            addCode(codes, "DEBT_WALLET_REQUIRED");
        }
        addCode(codes, "DEBT_CONFIRM_NOT_SUPPORTED");
    }

    private BusinessException debtUnsupported() {
        return new BusinessException("DEBT_CONFIRM_NOT_SUPPORTED", "MoneyFlow understood this debt draft, but automatic debt saving is not enabled yet.", HttpStatus.CONFLICT);
    }

    private String counterpartyName(VoiceReviewDraftType type, String text) {
        if (!debtLike(type)) return null;
        String normalized = normalize(text);
        if (normalized == null) return null;
        String[] words = normalized.split("\\s+");
        for (String word : words) {
            String lower = word.toLowerCase(Locale.ROOT);
            if (!List.of("toi", "minh", "cho", "muon", "vay", "tra", "no", "thu", "thanh", "toan", "dong", "tien", "lai").contains(lower)
                    && !lower.matches("\\d+.*|k|nghin|ngan|trieu|tr|dong|vnd")) {
                return word;
            }
        }
        return null;
    }

    private List<String> savingsNeeds(List<String> fields) {
        List<String> needs = new ArrayList<>(fields == null ? List.of() : fields);
        needs.remove("categoryId");
        needs.remove("savingsGoalId");
        needs.remove("sinkingFundId");
        needs.remove("emergencyFundId");
        needs.add("sourceWalletId");
        needs.add("targetFundId");
        return needs.stream().distinct().toList();
    }

    private void addSavingsWarnings(List<String> codes) {
        addCode(codes, "SAVINGS_NOT_EXPENSE");
        addCode(codes, "SAVINGS_SOURCE_WALLET_REQUIRED");
        addCode(codes, "SAVINGS_TARGET_NOT_SELECTED");
        addCode(codes, "SAVINGS_CONFIRM_NOT_SUPPORTED");
    }

    private void addCode(List<String> codes, String code) {
        if (!codes.contains(code)) {
            codes.add(code);
        }
    }

    private BusinessException savingsUnsupported() {
        return new BusinessException("SAVINGS_CONFIRM_NOT_SUPPORTED", "Savings and fund drafts need a target before saving.", HttpStatus.CONFLICT);
    }

    private String savingsTargetCandidate(VoiceReviewDraftType type, String text) {
        if (type == VoiceReviewDraftType.EMERGENCY_FUND_CONTRIBUTION) return "quy khan cap";
        if (type == VoiceReviewDraftType.SINKING_FUND_CONTRIBUTION) return normalize(text);
        if (type == VoiceReviewDraftType.SAVINGS_ALLOCATION || type == VoiceReviewDraftType.SAVINGS) return normalize(text);
        return null;
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

    private List<VoiceReviewDraftResponse.Warning> warnings(List<String> codes) {
        if (codes == null) return List.of();
        return codes.stream()
                .distinct()
                .map(code -> VoiceReviewDraftResponse.Warning.builder()
                        .code(code)
                        .field(warningField(code))
                        .message(warningMessage(code))
                        .build())
                .toList();
    }

    private String warningField(String code) {
        return switch (code) {
            case "INCOME_WALLET_NOT_SELECTED", "MISSING_WALLET", "WALLET_SNAPSHOT_WALLET_NOT_MATCHED", "WALLET_MATCH_AMBIGUOUS", "WALLET_SNAPSHOT_NO_WALLET" -> "walletId";
            case "SAVINGS_SOURCE_WALLET_REQUIRED" -> "sourceWalletId";
            case "SAVINGS_TARGET_NOT_SELECTED" -> "targetFundId";
            case "DEBT_WALLET_REQUIRED" -> "walletId";
            case "DEBT_COUNTERPARTY_REQUIRED", "DEBT_COUNTERPARTY_AMBIGUOUS" -> "counterpartyId";
            case "DEBT_NOT_FOUND", "DEBT_MATCH_AMBIGUOUS" -> "debtId";
            case "MISSING_CATEGORY", "CATEGORY_TYPE_MISMATCH", "UNKNOWN_CATEGORY" -> "categoryId";
            case "MISSING_INCOMESOURCE" -> "incomeSourceId";
            default -> null;
        };
    }

    private String warningMessage(String code) {
        if ("DEBT_NOT_EXPENSE".equals(code)) return "Debt movements are not normal expenses.";
        if ("DEBT_NOT_INCOME".equals(code)) return "Debt movements are not normal income.";
        if ("DEBT_COUNTERPARTY_REQUIRED".equals(code)) return "Choose the counterparty before saving this debt draft.";
        if ("DEBT_COUNTERPARTY_AMBIGUOUS".equals(code)) return "Multiple counterparties match. Choose the correct one.";
        if ("DEBT_WALLET_REQUIRED".equals(code)) return "Choose the source or destination wallet before saving this debt draft.";
        if ("DEBT_MATCH_AMBIGUOUS".equals(code)) return "Multiple debts match. Choose the correct debt.";
        if ("DEBT_NOT_FOUND".equals(code)) return "Choose an existing open debt before recording this payment.";
        if ("DEBT_CONFIRM_NOT_SUPPORTED".equals(code)) return "MoneyFlow understood this debt draft, but automatic debt saving is not enabled yet.";
        if ("DEBT_QUERY_USE_ASK_MODE".equals(code)) return "This is a debt question. MoneyFlow will answer read-only and will not create a transaction.";
        return switch (code) {
            case "MULTIPLE_AMOUNTS_DETECTED", "MULTIPLE_ITEMS_DETECTED" -> "Đã phát hiện nhiều khoản, hãy kiểm tra từng dòng trước khi lưu.";
            case "VOICE_MULTI_INTENT_DETECTED" -> "MoneyFlow phát hiện nhiều khoản trong một câu. Hãy kiểm tra từng dòng trước khi lưu.";
            case "INCOME_WALLET_NOT_SELECTED", "MISSING_WALLET" -> "Chọn ví trước khi lưu khoản này.";
            case "INCOME_FACT_NO_WALLET_EFFECT" -> "Khoản này ghi nhận thu nhập, nhưng không cộng vào ví nào. Số dư ví sẽ được kiểm tra qua chốt sổ.";
            case "INCOME_SPLIT_NOT_SUPPORTED" -> "MoneyFlow chưa tự chia khoản thu này vào nhiều ví. Hãy kiểm tra lại hoặc dùng chốt sổ để cập nhật số dư ví.";
            case "WALLET_SNAPSHOT_WALLET_NOT_MATCHED" -> "Chưa xác định được ví cần cập nhật số dư.";
            case "WALLET_MATCH_AMBIGUOUS" -> "Có nhiều ví giống tên này. Vui lòng chọn ví chính xác.";
            case "WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED" -> "MoneyFlow đã hiểu đây là số dư ví, nhưng luồng lưu snapshot chưa bật.";
            case "WALLET_SNAPSHOT_NO_WALLET" -> "Chọn ví trước khi lưu số dư kiểm tra.";
            case "UNSUPPORTED_INTENT", "DRAFT_UNSUPPORTED_TYPE", "VOICE_INTENT_NOT_COMMITTABLE", "VOICE_SEGMENT_UNSUPPORTED" -> "MoneyFlow chưa hỗ trợ lưu ý này tự động.";
            case "CATEGORY_TYPE_MISMATCH" -> "Danh mục không khớp loại khoản.";
            case "MISSING_CATEGORY", "UNKNOWN_CATEGORY" -> "Chọn danh mục trước khi lưu khoản này.";
            case "MISSING_INCOMESOURCE" -> "Chọn nguồn thu trước khi lưu khoản này.";
            default -> "Hãy kiểm tra khoản này trước khi lưu.";
        };
    }

    private boolean supported(VoiceReviewDraftType type) {
        return type == VoiceReviewDraftType.EXPENSE || type == VoiceReviewDraftType.INCOME || type == VoiceReviewDraftType.INCOME_FACT;
    }

    private boolean canConfirm(VoiceReviewDraftResponse.Candidate candidate) {
        if (candidate == null || !supported(candidate.getType())) return false;
        if (candidate.getAmount() == null || candidate.getAmount().compareTo(BigDecimal.ZERO) <= 0) return false;
        if (candidate.getOccurredAt() == null) return false;
        if (candidate.getType() == VoiceReviewDraftType.EXPENSE) return candidate.getCategoryId() != null;
        if (candidate.getType() == VoiceReviewDraftType.INCOME) return candidate.getWalletId() != null && candidate.getIncomeSourceId() != null;
        return true;
    }

    private VoiceReviewDraftRequest toRequest(VoiceReviewDraftResponse.Candidate candidate) {
        VoiceReviewDraftRequest req = new VoiceReviewDraftRequest();
        req.setType(candidate.getType());
        req.setAmount(candidate.getAmount());
        req.setOccurredAt(candidate.getOccurredAt());
        req.setWalletId(candidate.getWalletId());
        req.setSourceWalletId(candidate.getSourceWalletId());
        req.setCategoryId(candidate.getCategoryId());
        req.setIncomeSourceId(candidate.getIncomeSourceId());
        req.setDebtDirection(candidate.getDebtDirection());
        req.setCounterpartyId(candidate.getCounterpartyId());
        req.setCounterpartyName(candidate.getCounterpartyName());
        req.setDebtId(candidate.getDebtId());
        req.setTargetFundId(candidate.getTargetFundId());
        req.setDestinationWalletId(candidate.getDestinationWalletId());
        req.setJarId(candidate.getJarId());
        req.setNote(candidate.getNote());
        req.setScope(candidate.getScope());
        return req;
    }

    private VoiceReviewDraftResponse.DraftItem requireDraft(UUID workspaceId, VoiceRecord record, String draftId, UUID userId) {
        String normalizedDraftId = normalize(draftId);
        if (normalizedDraftId == null) {
            throw new BusinessException("VOICE_DRAFT_NOT_FOUND", "Không tìm thấy draft giọng nói.", HttpStatus.NOT_FOUND);
        }
        QuickEntryPreviewResponse preview = quickEntryService.parse(workspaceId,
                record.getEditedTranscript() != null ? record.getEditedTranscript() : record.getOriginalTranscript(),
                userId);
        Workspace workspace = record.getWorkspace();
        return drafts(workspace, preview).stream()
                .filter(draft -> normalizedDraftId.equals(draft.getDraftId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("VOICE_DRAFT_NOT_FOUND", "Không tìm thấy draft giọng nói.", HttpStatus.NOT_FOUND));
    }

    private String draftSourceReference(UUID voiceRecordId, String draftId) {
        return "voice-review:" + voiceRecordId + ":" + normalize(draftId);
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
