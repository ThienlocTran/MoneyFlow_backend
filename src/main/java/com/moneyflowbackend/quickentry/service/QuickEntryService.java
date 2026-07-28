package com.moneyflowbackend.quickentry.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.quickentry.dto.QuickEntryBatchConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryBatchConfirmResponse;
import com.moneyflowbackend.quickentry.dto.QuickEntryButtonRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryOptionsResponse;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.dto.VoiceCandidateStatus;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
import com.moneyflowbackend.quickentry.parser.QuickEntryParser;
import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;
import com.moneyflowbackend.transaction.dto.TransactionRequest;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.service.TransactionService;
import com.moneyflowbackend.voice.model.VoiceRecord;
import com.moneyflowbackend.voice.model.VoiceRecordStatus;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspacePerson;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspacePersonRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class QuickEntryService {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";
    private static final String VOICE_BATCH_IDEMPOTENCY_PREFIX = "voice-batch:";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryKeywordRepository keywordRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final WorkspacePersonRepository workspacePersonRepository;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final VoiceRecordRepository voiceRecordRepository;
    private final UserRepository userRepository;
    private final QuickEntryParser parser;
    private final Clock clock;

    public QuickEntryService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WalletRepository walletRepository,
            CategoryRepository categoryRepository,
            CategoryKeywordRepository keywordRepository,
            IncomeSourceRepository incomeSourceRepository,
            WorkspacePersonRepository workspacePersonRepository,
            TransactionService transactionService,
            TransactionRepository transactionRepository,
            VoiceRecordRepository voiceRecordRepository,
            UserRepository userRepository,
            QuickEntryParser parser,
            Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.keywordRepository = keywordRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.workspacePersonRepository = workspacePersonRepository;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.voiceRecordRepository = voiceRecordRepository;
        this.userRepository = userRepository;
        this.parser = parser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QuickEntryOptionsResponse options(UUID workspaceId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        List<Wallet> wallets = activeWallets(workspaceId);
        List<Category> quickCategories = activeCategories(workspaceId).stream()
                .filter(Category::isQuickAction)
                .filter(category -> category.getCategoryType() == CategoryType.INCOME || category.getCategoryType() == CategoryType.EXPENSE)
                .toList();
        UUID defaultWalletId = wallets.stream().filter(Wallet::isDefault).findFirst().map(Wallet::getId).orElse(null);
        return QuickEntryOptionsResponse.builder()
                .defaultWalletId(defaultWalletId)
                .wallets(wallets.stream().map(wallet -> QuickEntryOptionsResponse.WalletOption.builder()
                        .id(wallet.getId())
                        .name(wallet.getName())
                        .type(wallet.getWalletType().name())
                        .isDefault(wallet.isDefault())
                        .build()).toList())
                .quickCategories(quickCategories.stream().map(category -> QuickEntryOptionsResponse.CategoryOption.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .type(category.getCategoryType().name())
                        .icon(category.getIcon())
                        .jarId(category.getJar() == null ? null : category.getJar().getId())
                        .jarName(category.getJar() == null ? null : category.getJar().getName())
                        .build()).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public QuickEntryPreviewResponse parse(UUID workspaceId, String text, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        Workspace workspace = member.getWorkspace();
        String raw = text == null ? "" : text;
        if (raw.trim().isEmpty()) {
            throw new BusinessException("QUICK_ENTRY_TEXT_REQUIRED", "Quick entry text is required");
        }
        List<CategoryKeyword> keywords = keywords(workspaceId);
        List<Category> categories = activeCategories(workspaceId);
        List<Wallet> wallets = activeWallets(workspaceId);
        QuickEntryPreviewResponse preview = parser.parse(raw, workspace, keywords, categories, wallets);
        UUID suggestedWalletId = suggestedWalletId(workspaceId, userId, preview);
        if (suggestedWalletId != null && preview.getMatchedWalletText() == null && !suggestedWalletId.equals(preview.getWalletId())) {
            preview = parser.parse(raw, workspace, keywords, categories, wallets, suggestedWalletId);
        }
        return applyIncomeSourceDefaults(workspaceId, userId, member, preview);
    }

    @Transactional
    public TransactionResponse confirm(UUID workspaceId, QuickEntryConfirmRequest req, UUID userId) {
        return confirmWithSource(workspaceId, req, userId, TransactionSourceType.QUICK_TEXT);
    }

    @Transactional
    public TransactionResponse confirmVoice(UUID workspaceId, QuickEntryConfirmRequest req, UUID userId) {
        rejectUnsupportedVoiceIntent(req);
        String idempotencyKey = requireVoiceIdempotencyKey(req);
        var existingVoiceRecord = voiceRecordRepository.findVoiceIdempotencyMatch(workspaceId, userId, idempotencyKey);
        if (existingVoiceRecord.isPresent()) {
            return transactionRepository.findByWorkspaceIdAndVoiceRecordIdAndSourceType(
                            workspaceId, existingVoiceRecord.get().getId(), TransactionSourceType.VOICE)
                    .map(transactionService::mapExistingToResponse)
                    .orElseThrow(() -> new BusinessException("VOICE_CONFIRM_INCOMPLETE", "Voice confirmation is incomplete"));
        }
        return confirmWithSource(workspaceId, req, userId, TransactionSourceType.VOICE);
    }

    @Transactional
    public QuickEntryBatchConfirmResponse confirmVoiceBatch(UUID workspaceId, QuickEntryBatchConfirmRequest req, UUID userId) {
        Workspace workspace = requireWritableMember(workspaceId, userId).getWorkspace();
        if (req == null) {
            throw new BusinessException("QUICK_ENTRY_BATCH_REQUIRED", "Voice batch request is required");
        }
        String idempotencyKey = normalize(req.getIdempotencyKey());
        if (idempotencyKey == null) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency key is required");
        }
        String storedIdempotencyKey = batchIdempotencyKey(idempotencyKey);
        var existingVoiceRecord = voiceRecordRepository.findVoiceIdempotencyMatch(workspaceId, userId, storedIdempotencyKey);
        if (existingVoiceRecord.isPresent()) {
            return replayBatch(idempotencyKey, workspaceId, existingVoiceRecord.get().getId());
        }

        List<QuickEntryBatchConfirmRequest.CandidateConfirmRequest> selected = req.getCandidates() == null
                ? List.of()
                : req.getCandidates().stream()
                .filter(candidate -> !Boolean.FALSE.equals(candidate.getSelected()))
                .toList();
        if (selected.isEmpty()) {
            throw new BusinessException("VOICE_BATCH_EMPTY", "At least one selected candidate is required");
        }
        for (QuickEntryBatchConfirmRequest.CandidateConfirmRequest candidate : selected) {
            requireCommittableVoiceCandidate(candidate);
        }
        VoiceRecord voiceRecord = voiceRecordRepository.saveAndFlush(VoiceRecord.builder()
                .workspace(workspace)
                .createdByUser(userRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND)))
                .audioUrl(null)
                .storagePublicId(null)
                .mimeType(normalize(req.getAudioMimeType()))
                .durationSeconds(req.getDurationSeconds())
                .fileSizeBytes(null)
                .originalTranscript(normalize(req.getRawInput()))
                .editedTranscript(normalize(req.getRawInput()))
                .idempotencyKey(storedIdempotencyKey)
                .voiceStatus(VoiceRecordStatus.CONFIRMED)
                .build());

        List<QuickEntryBatchConfirmResponse.Item> items = new ArrayList<>();
        for (QuickEntryBatchConfirmRequest.CandidateConfirmRequest candidate : selected) {
            String candidateId = candidateId(candidate);
            TransactionRequest txReq = toTransactionRequest(workspace, candidate);
            TransactionResponse tx = transactionService.createWithSource(
                    workspaceId,
                    txReq,
                    userId,
                    TransactionSourceType.VOICE,
                    req.getRawInput(),
                    voiceRecord.getId(),
                    candidateId);
            items.add(QuickEntryBatchConfirmResponse.Item.builder()
                    .candidateId(candidateId)
                    .transaction(tx)
                    .build());
        }
        return QuickEntryBatchConfirmResponse.builder()
                .idempotencyKey(idempotencyKey)
                .voiceRecordId(voiceRecord.getId())
                .committedCount(items.size())
                .idempotentReplay(false)
                .items(items)
                .build();
    }

    private TransactionResponse confirmWithSource(UUID workspaceId, QuickEntryConfirmRequest req, UUID userId, TransactionSourceType sourceType) {
        Workspace workspace = requireWritableMember(workspaceId, userId).getWorkspace();
        TransactionRequest txReq = toTransactionRequest(workspace, req, sourceType);
        learnKeywordIfRequested(workspace, req);
        if (sourceType == TransactionSourceType.VOICE) {
            VoiceRecord voiceRecord = voiceRecordRepository.save(VoiceRecord.builder()
                    .workspace(workspace)
                    .createdByUser(userRepository.findById(userId)
                            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND)))
                    .audioUrl(null)
                    .storagePublicId(null)
                    .mimeType(normalize(req.getAudioMimeType()))
                    .durationSeconds(req.getDurationSeconds())
                    .fileSizeBytes(null)
                    .originalTranscript(normalize(req.getRawInput()))
                    .editedTranscript(normalize(req.getRawInput()))
                    .idempotencyKey(sourceType == TransactionSourceType.VOICE ? requireVoiceIdempotencyKey(req) : null)
                    .voiceStatus(VoiceRecordStatus.CONFIRMED)
                    .build());
            return transactionService.createWithSource(workspaceId, txReq, userId, sourceType, req.getRawInput(), voiceRecord.getId(), voiceSourceReference(req));
        }
        return transactionService.createWithSource(workspaceId, txReq, userId, sourceType, req.getRawInput());
    }

    @Transactional
    public TransactionResponse button(UUID workspaceId, QuickEntryButtonRequest req, UUID userId) {
        Workspace workspace = requireWritableMember(workspaceId, userId).getWorkspace();
        if (req.getCategoryId() == null) {
            throw new BusinessException("CATEGORY_NOT_FOUND", "Category is required");
        }
        Category category = categoryRepository.findByIdAndWorkspaceId(req.getCategoryId(), workspaceId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
        if (!category.isActive()) {
            throw new BusinessException("CATEGORY_INACTIVE", "Category is inactive");
        }
        if (category.isArchived()) {
            throw new BusinessException("CATEGORY_ARCHIVED", "Category is archived");
        }
        if (!category.isQuickAction()) {
            throw new BusinessException("CATEGORY_NOT_QUICK_ACTION", "Category is not a quick action");
        }
        if (category.getCategoryType() != CategoryType.INCOME && category.getCategoryType() != CategoryType.EXPENSE) {
            throw new BusinessException("INVALID_TRANSACTION_TYPE", "Invalid quick button category type");
        }

        TransactionRequest txReq = new TransactionRequest();
        txReq.setType(category.getCategoryType() == CategoryType.INCOME ? TransactionType.INCOME : TransactionType.EXPENSE);
        txReq.setAmount(requireAmount(req.getAmount()));
        LocalDate date = req.getTransactionDate() == null ? today(workspace) : req.getTransactionDate();
        txReq.setTransactionDate(date);
        txReq.setStatus(statusFor(date, workspace));
        txReq.setTransactionTime(req.getTransactionTime());
        txReq.setDescription(req.getDescription());
        txReq.setNote(req.getNote());
        if (req.hasSpendingScope()) {
            txReq.setSpendingScope(req.getSpendingScope());
        }
        txReq.setAttributedPersonId(req.getAttributedPersonId());
        txReq.setCategoryId(category.getId());
        txReq.setWalletId(req.getWalletId() == null ? defaultWalletId(workspaceId) : req.getWalletId());
        if (txReq.getWalletId() == null) {
            throw new BusinessException("QUICK_ENTRY_WALLET_NOT_FOUND", "Default wallet not found");
        }
        return transactionService.createWithSource(workspaceId, txReq, userId, TransactionSourceType.QUICK_BUTTON, null);
    }

    private TransactionRequest toTransactionRequest(Workspace workspace, QuickEntryConfirmRequest req, TransactionSourceType sourceType) {
        if (req == null) {
            throw new BusinessException("QUICK_ENTRY_NOT_READY", "Quick entry request is required");
        }
        if (req.getType() == null) {
            throw new BusinessException("INVALID_TRANSACTION_TYPE", "Transaction type is required");
        }
        BigDecimal amount = requireAmount(req.getAmount());
        LocalDate date = req.getTransactionDate();
        if (date == null) {
            throw new BusinessException("INVALID_DATE", "Transaction date is required");
        }
        TransactionRequest txReq = new TransactionRequest();
        txReq.setType(req.getType());
        txReq.setStatus(req.getStatus() == null ? statusFor(date, workspace) : req.getStatus());
        txReq.setAmount(amount);
        txReq.setTransactionDate(date);
        txReq.setTransactionTime(req.getTransactionTime());
        txReq.setDescription(req.getDescription());
        txReq.setNote(req.getNote());
        txReq.setAttributedPersonId(req.getAttributedPersonId());
        if (req.hasSpendingScope()) {
            txReq.setSpendingScope(req.getSpendingScope());
        }
        if (req.getType() == TransactionType.TRANSFER) {
            if (req.getSourceWalletId() == null) {
                throw new BusinessException("TRANSFER_SOURCE_REQUIRED", "Transfer source wallet is required");
            }
            if (req.getDestinationWalletId() == null) {
                throw new BusinessException("TRANSFER_DESTINATION_REQUIRED", "Transfer destination wallet is required");
            }
            if (req.getSourceWalletId().equals(req.getDestinationWalletId())) {
                throw new BusinessException("TRANSFER_SAME_WALLET", "Transfer wallets must be different");
            }
            txReq.setSourceWalletId(req.getSourceWalletId());
            txReq.setDestinationWalletId(req.getDestinationWalletId());
        } else if (req.getType() == TransactionType.INCOME || req.getType() == TransactionType.EXPENSE) {
            if (req.getWalletId() == null && sourceType != TransactionSourceType.VOICE) {
                throw new BusinessException("QUICK_ENTRY_WALLET_NOT_FOUND", "Wallet is required");
            }
            if (req.getType() == TransactionType.EXPENSE && req.getCategoryId() == null) {
                throw new BusinessException("QUICK_ENTRY_CATEGORY_NOT_FOUND", "Category is required");
            }
            txReq.setWalletId(req.getWalletId());
            if (req.getType() == TransactionType.INCOME) {
                if (req.getIncomeSourceId() == null) {
                    throw new BusinessException("INCOME_SOURCE_REQUIRED", "Income source is required");
                }
                txReq.setIncomeSourceId(req.getIncomeSourceId());
            } else {
                txReq.setCategoryId(req.getCategoryId());
                txReq.setRelatedIncomeSourceId(req.getRelatedIncomeSourceId());
            }
        } else {
            throw new BusinessException("INVALID_TRANSACTION_TYPE", "Invalid transaction type");
        }
        return txReq;
    }

    private TransactionRequest toTransactionRequest(Workspace workspace, QuickEntryBatchConfirmRequest.CandidateConfirmRequest req) {
        if (req == null) {
            throw new BusinessException("QUICK_ENTRY_NOT_READY", "Quick entry candidate is required");
        }
        QuickEntryConfirmRequest single = new QuickEntryConfirmRequest();
        single.setType(req.getType());
        single.setStatus(req.getStatus());
        single.setAmount(req.getAmount());
        single.setWalletId(req.getWalletId());
        single.setCategoryId(req.getCategoryId());
        single.setIncomeSourceId(req.getIncomeSourceId());
        single.setRelatedIncomeSourceId(req.getRelatedIncomeSourceId());
        single.setSourceWalletId(req.getSourceWalletId());
        single.setDestinationWalletId(req.getDestinationWalletId());
        single.setTransactionDate(req.getTransactionDate());
        single.setTransactionTime(req.getTransactionTime());
        single.setDescription(req.getDescription());
        single.setNote(req.getNote());
        single.setAttributedPersonId(req.getAttributedPersonId());
        if (req.hasSpendingScope()) {
            single.setSpendingScope(req.getSpendingScope());
        }
        return toTransactionRequest(workspace, single, TransactionSourceType.VOICE);
    }

    private String candidateId(QuickEntryBatchConfirmRequest.CandidateConfirmRequest candidate) {
        String id = normalize(candidate.getCandidateId());
        if (id == null) {
            id = normalize(candidate.getClientCandidateId());
        }
        if (id == null) {
            throw new BusinessException("CANDIDATE_ID_REQUIRED", "Candidate id is required");
        }
        return id;
    }

    private String batchIdempotencyKey(String idempotencyKey) {
        return VOICE_BATCH_IDEMPOTENCY_PREFIX + idempotencyKey;
    }

    private QuickEntryBatchConfirmResponse replayBatch(String idempotencyKey, UUID workspaceId, UUID voiceRecordId) {
        var replayedTransactions = transactionRepository
                .findAllByWorkspaceIdAndVoiceRecordIdAndSourceTypeOrderByCreatedAtAsc(
                        workspaceId, voiceRecordId, TransactionSourceType.VOICE);
        if (replayedTransactions.isEmpty()) {
            throw new BusinessException("VOICE_CONFIRM_INCOMPLETE", "Voice batch confirmation is incomplete");
        }
        List<QuickEntryBatchConfirmResponse.Item> replayedItems = replayedTransactions.stream()
                .map(tx -> QuickEntryBatchConfirmResponse.Item.builder()
                        .candidateId(tx.getSourceReference())
                        .transaction(transactionService.mapToResponse(tx))
                        .build())
                .toList();
        return QuickEntryBatchConfirmResponse.builder()
                .idempotencyKey(idempotencyKey)
                .voiceRecordId(voiceRecordId)
                .committedCount(replayedItems.size())
                .idempotentReplay(true)
                .items(replayedItems)
                .build();
    }

    private QuickEntryPreviewResponse applyIncomeSourceDefaults(UUID workspaceId, UUID userId, WorkspaceMember member, QuickEntryPreviewResponse preview) {
        if (preview == null) {
            return null;
        }
        List<IncomeSource> sources = incomeSourceRepository.findAllByWorkspaceIdAndStatusOrderByNameAsc(workspaceId, IncomeSourceStatus.ACTIVE);
        List<WorkspacePerson> people = workspacePersonRepository.findAllByWorkspaceId(workspaceId).stream()
                .filter(WorkspacePerson::isActive)
                .toList();
        applyIncomeSourceDefault(userId, member, preview.getNormalizedInput(), preview, sources, people);
        for (QuickEntryPreviewResponse.Candidate candidate : preview.getCandidates()) {
            applyIncomeSourceDefault(userId, member,
                    candidate.getOriginalText() == null ? preview.getNormalizedInput() : candidate.getOriginalText(),
                    candidate,
                    sources,
                    people);
        }
        return preview;
    }

    private void applyIncomeSourceDefault(
            UUID userId,
            WorkspaceMember member,
            String text,
            QuickEntryPreviewResponse preview,
            List<IncomeSource> sources,
            List<WorkspacePerson> people) {
        if (preview.getType() != TransactionType.INCOME) {
            return;
        }
        IncomeSource source = defaultIncomeSource(userId, member, text, sources, people);
        if (source == null) {
            addMissing(preview.getMissingFields(), "incomeSource");
            preview.setCandidateStatus(VoiceCandidateStatus.NEEDS_REVIEW);
            preview.setReadyToConfirm(false);
            preview.setCommitSupported(false);
            return;
        }
        preview.setIncomeSourceId(source.getId());
        preview.setIncomeSourceName(source.getName());
        preview.setCategoryId(null);
        preview.setCategoryName(null);
        markIncomeReady(preview.getMissingFields(), preview.getWarnings());
        boolean ready = preview.getAmount() != null && preview.getWalletId() != null && preview.getTransactionDate() != null
                && preview.getMissingFields().isEmpty();
        preview.setCandidateStatus(ready ? VoiceCandidateStatus.READY : VoiceCandidateStatus.NEEDS_REVIEW);
        preview.setReadyToConfirm(ready);
        preview.setCommitSupported(ready);
        preview.setConfidence(ready ? 0.95 : preview.getConfidence());
    }

    private void applyIncomeSourceDefault(
            UUID userId,
            WorkspaceMember member,
            String text,
            QuickEntryPreviewResponse.Candidate candidate,
            List<IncomeSource> sources,
            List<WorkspacePerson> people) {
        if (candidate.getType() != TransactionType.INCOME) {
            return;
        }
        IncomeSource source = defaultIncomeSource(userId, member, text, sources, people);
        if (source == null) {
            addMissing(candidate.getMissingFields(), "incomeSource");
            candidate.setCandidateStatus(VoiceCandidateStatus.NEEDS_REVIEW);
            candidate.setReadyToConfirm(false);
            candidate.setCommitSupported(false);
            candidate.setValidationStatus("NEEDS_REVIEW");
            return;
        }
        candidate.setIncomeSourceId(source.getId());
        candidate.setIncomeSourceName(source.getName());
        candidate.setCategoryId(null);
        candidate.setCategoryName(null);
        markIncomeReady(candidate.getMissingFields(), candidate.getWarnings());
        boolean ready = candidate.getAmount() != null && candidate.getWalletId() != null && candidate.getTransactionDate() != null
                && candidate.getMissingFields().isEmpty();
        candidate.setCandidateStatus(ready ? VoiceCandidateStatus.READY : VoiceCandidateStatus.NEEDS_REVIEW);
        candidate.setReadyToConfirm(ready);
        candidate.setCommitSupported(ready);
        candidate.setValidationStatus(ready ? "READY" : "NEEDS_REVIEW");
        candidate.setConfidence(ready ? 0.95 : candidate.getConfidence());
    }

    private IncomeSource defaultIncomeSource(
            UUID userId,
            WorkspaceMember member,
            String text,
            List<IncomeSource> sources,
            List<WorkspacePerson> people) {
        IncomeSpeaker speaker = explicitSpeaker(text, people);
        if (speaker == null) {
            speaker = authenticatedSpeaker(userId, member, people);
        }
        if (speaker == null) {
            return null;
        }
        String normalizedOwner = VietnameseTextNormalizer.comparable(speaker == IncomeSpeaker.EM
                ? "Thu nhập của em"
                : "Thu nhập của anh");
        return sources.stream()
                .filter(source -> VietnameseTextNormalizer.comparable(source.getName()).equals(normalizedOwner))
                .findFirst()
                .orElse(null);
    }

    private IncomeSpeaker explicitSpeaker(String text, List<WorkspacePerson> people) {
        String normalized = VietnameseTextNormalizer.comparable(text);
        if (containsToken(normalized, "em") || containsToken(normalized, "tam")) {
            return IncomeSpeaker.EM;
        }
        if (containsToken(normalized, "anh") || containsToken(normalized, "thien") || containsPhrase(normalized, "thien loc")) {
            return IncomeSpeaker.ANH;
        }
        for (WorkspacePerson person : people) {
            IncomeSpeaker speaker = speakerFromIdentity(person.getDisplayName());
            String name = VietnameseTextNormalizer.comparable(person.getDisplayName());
            if (speaker != null && !name.isBlank() && containsPhrase(normalized, name)) {
                return speaker;
            }
        }
        return null;
    }

    private IncomeSpeaker authenticatedSpeaker(UUID userId, WorkspaceMember member, List<WorkspacePerson> people) {
        if (member != null) {
            IncomeSpeaker byPerson = speakerFromPerson(member.getPerson());
            if (byPerson != null) {
                return byPerson;
            }
            IncomeSpeaker byUser = speakerFromUser(member.getUser());
            if (byUser != null) {
                return byUser;
            }
        }
        return people.stream()
                .filter(person -> person.getLinkedUser() != null && person.getLinkedUser().getId().equals(userId))
                .map(this::speakerFromPerson)
                .filter(speaker -> speaker != null)
                .findFirst()
                .orElse(null);
    }

    private IncomeSpeaker speakerFromPerson(WorkspacePerson person) {
        return person == null ? null : speakerFromIdentity(person.getDisplayName());
    }

    private IncomeSpeaker speakerFromUser(com.moneyflowbackend.auth.model.User user) {
        if (user == null) {
            return null;
        }
        IncomeSpeaker speaker = speakerFromIdentity(user.getFullName());
        if (speaker != null) {
            return speaker;
        }
        speaker = speakerFromIdentity(user.getUsername());
        if (speaker != null) {
            return speaker;
        }
        String email = normalize(user.getEmail());
        int at = email == null ? -1 : email.indexOf('@');
        return speakerFromIdentity(at < 0 ? email : email.substring(0, at));
    }

    private IncomeSpeaker speakerFromIdentity(String value) {
        String normalized = VietnameseTextNormalizer.comparable(value);
        if (containsToken(normalized, "em") || containsToken(normalized, "tam")) {
            return IncomeSpeaker.EM;
        }
        if (containsToken(normalized, "anh") || containsToken(normalized, "thien") || containsPhrase(normalized, "thien loc")) {
            return IncomeSpeaker.ANH;
        }
        return null;
    }

    private boolean containsToken(String normalized, String token) {
        return normalized != null && normalized.matches(".*(?<!\\S)" + java.util.regex.Pattern.quote(token) + "(?!\\S).*");
    }

    private boolean containsPhrase(String normalized, String phrase) {
        return normalized != null && phrase != null && !phrase.isBlank()
                && normalized.matches(".*(?<!\\S)" + java.util.regex.Pattern.quote(phrase) + "(?!\\S).*");
    }

    private void markIncomeReady(List<String> missingFields, List<String> warnings) {
        missingFields.removeIf(field -> field.equals("incomeSource") || field.equals("CATEGORY"));
        if (warnings != null) {
            warnings.removeIf(warning -> warning.equals("UNKNOWN_CATEGORY"));
        }
    }

    private void addMissing(List<String> missingFields, String field) {
        if (!missingFields.contains(field)) {
            missingFields.add(field);
        }
    }

    private enum IncomeSpeaker {
        ANH,
        EM
    }

    private void learnKeywordIfRequested(Workspace workspace, QuickEntryConfirmRequest req) {
        String keyword = VietnameseTextNormalizer.compact(req.getLearnKeyword());
        if (keyword.isBlank()) {
            return;
        }
        if (req.getCategoryId() == null) {
            throw new BusinessException("QUICK_ENTRY_CATEGORY_NOT_FOUND", "Category is required to learn keyword");
        }
        Category category = categoryRepository.findByIdAndWorkspaceId(req.getCategoryId(), workspace.getId())
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
        String normalized = VietnameseTextNormalizer.comparable(keyword);
        for (CategoryKeyword existing : keywordRepository.findAllByWorkspaceIdOrderByPriorityDescKeywordAsc(workspace.getId())) {
            if (VietnameseTextNormalizer.comparable(existing.getKeyword()).equals(normalized)) {
                if (existing.getCategory().getId().equals(category.getId())) {
                    return;
                }
                throw new BusinessException("KEYWORD_ALREADY_EXISTS", "Keyword already exists for another category");
            }
        }
        keywordRepository.save(CategoryKeyword.builder()
                .workspace(workspace)
                .category(category)
                .keyword(keyword)
                .priority(0)
                .isUserLearned(true)
                .build());
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Amount must be greater than 0");
        }
        return amount;
    }

    private List<Wallet> activeWallets(UUID workspaceId) {
        return walletRepository.findAllByWorkspaceIdAndIsActiveTrue(workspaceId).stream()
                .sorted(Comparator.comparing(Wallet::isDefault).reversed().thenComparing(Wallet::getName))
                .toList();
    }

    private List<Category> activeCategories(UUID workspaceId) {
        return categoryRepository.findAllByWorkspaceIdOrderByDisplayOrderAsc(workspaceId).stream()
                .filter(Category::isActive)
                .filter(category -> !category.isArchived())
                .toList();
    }

    private List<CategoryKeyword> keywords(UUID workspaceId) {
        return keywordRepository.findAllByWorkspaceIdOrderByPriorityDescKeywordAsc(workspaceId).stream()
                .filter(keyword -> keyword.getCategory() != null)
                .filter(keyword -> keyword.getCategory().isActive())
                .filter(keyword -> !keyword.getCategory().isArchived())
                .toList();
    }

    private UUID defaultWalletId(UUID workspaceId) {
        return walletRepository.findByWorkspaceIdAndIsDefaultTrueAndIsActiveTrue(workspaceId)
                .map(Wallet::getId)
                .orElse(null);
    }

    private UUID suggestedWalletId(UUID workspaceId, UUID userId, QuickEntryPreviewResponse preview) {
        if (preview.getType() != TransactionType.INCOME && preview.getType() != TransactionType.EXPENSE) {
            return null;
        }
        return transactionRepository.findRecentActiveWalletSuggestions(workspaceId, userId, preview.getType()).stream()
                .findFirst()
                .map(Wallet::getId)
                .orElse(null);
    }

    private TransactionStatus statusFor(LocalDate date, Workspace workspace) {
        return date.isAfter(today(workspace)) ? TransactionStatus.PLANNED : TransactionStatus.POSTED;
    }

    private LocalDate today(Workspace workspace) {
        try {
            return LocalDate.now(clock.withZone(ZoneId.of(workspace.getTimezone() == null ? FALLBACK_ZONE : workspace.getTimezone())));
        } catch (DateTimeException ex) {
            return LocalDate.now(clock.withZone(ZoneId.of(FALLBACK_ZONE)));
        }
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        findWorkspace(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot create quick entries", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private Workspace findWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireVoiceIdempotencyKey(QuickEntryConfirmRequest req) {
        String key = req == null ? null : normalize(req.getIdempotencyKey());
        if (key == null) {
            throw new BusinessException("VOICE_IDEMPOTENCY_KEY_REQUIRED", "Voice idempotency key is required");
        }
        if (key.length() > 100) {
            throw new BusinessException("VOICE_IDEMPOTENCY_KEY_INVALID", "Voice idempotency key is invalid");
        }
        return key;
    }

    private void rejectUnsupportedVoiceIntent(QuickEntryConfirmRequest req) {
        rejectUnsupportedVoiceIntent(req == null ? null : req.getIntentType(), req == null ? null : req.getType());
    }

    private void requireCommittableVoiceCandidate(QuickEntryBatchConfirmRequest.CandidateConfirmRequest candidate) {
        String id = candidateId(candidate);
        if (candidate.getCandidateStatus() != VoiceCandidateStatus.READY) {
            throw new BusinessException(
                    "VOICE_CANDIDATE_NOT_READY",
                    "Candidate " + id + " is " + candidate.getCandidateStatus() + "; only READY transaction candidates can be committed");
        }
        rejectUnsupportedVoiceIntent(candidate.getIntentType(), candidate.getType(), id);
    }

    private void rejectUnsupportedVoiceIntent(VoiceIntentType intentType, TransactionType type) {
        rejectUnsupportedVoiceIntent(intentType, type, null);
    }

    private void rejectUnsupportedVoiceIntent(VoiceIntentType intentType, TransactionType type, String candidateId) {
        if (intentType == null) {
            return;
        }
        if (intentType == VoiceIntentType.TRANSACTION_EXPENSE && type == TransactionType.EXPENSE) {
            return;
        }
        if (intentType == VoiceIntentType.TRANSACTION_INCOME && type == TransactionType.INCOME) {
            return;
        }
        if (intentType == VoiceIntentType.TRANSACTION_TRANSFER && type == TransactionType.TRANSFER) {
            return;
        }
        String prefix = candidateId == null ? "Voice intent" : "Candidate " + candidateId + " intent " + intentType;
        throw new BusinessException("VOICE_INTENT_NOT_COMMITTABLE", prefix + " is not supported for commit as " + type);
    }

    private String voiceSourceReference(QuickEntryConfirmRequest req) {
        String key = normalize(req == null ? null : req.getIdempotencyKey());
        if (key == null) {
            return null;
        }
        if (key.length() > 180) {
            throw new BusinessException("VOICE_IDEMPOTENCY_KEY_INVALID", "Voice idempotency key is invalid");
        }
        String candidateId = normalize(req.getCandidateId());
        if (candidateId == null) {
            candidateId = normalize(req.getClientCandidateId());
        }
        String sourceReference = candidateId == null ? "voice:" + key : "voice:" + key + ":" + candidateId;
        if (sourceReference.length() > 255) {
            throw new BusinessException("VOICE_IDEMPOTENCY_KEY_INVALID", "Voice idempotency key is invalid");
        }
        return sourceReference;
    }
}
