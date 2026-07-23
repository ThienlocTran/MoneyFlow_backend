package com.moneyflowbackend.quickentry.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.quickentry.dto.QuickEntryButtonRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryConfirmRequest;
import com.moneyflowbackend.quickentry.dto.QuickEntryOptionsResponse;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
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
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class QuickEntryService {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryKeywordRepository keywordRepository;
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
        Workspace workspace = requireActiveMember(workspaceId, userId).getWorkspace();
        String raw = text == null ? "" : text;
        if (raw.trim().isEmpty()) {
            throw new BusinessException("QUICK_ENTRY_TEXT_REQUIRED", "Quick entry text is required");
        }
        List<CategoryKeyword> keywords = keywords(workspaceId);
        List<Category> categories = activeCategories(workspaceId);
        List<Wallet> wallets = activeWallets(workspaceId);
        QuickEntryPreviewResponse preview = parser.parse(raw, workspace, keywords, categories, wallets);
        UUID suggestedWalletId = suggestedWalletId(workspaceId, userId, preview);
        if (suggestedWalletId == null || preview.getMatchedWalletText() != null || suggestedWalletId.equals(preview.getWalletId())) {
            return preview;
        }
        return parser.parse(raw, workspace, keywords, categories, wallets, suggestedWalletId);
    }

    @Transactional
    public TransactionResponse confirm(UUID workspaceId, QuickEntryConfirmRequest req, UUID userId) {
        return confirmWithSource(workspaceId, req, userId, TransactionSourceType.QUICK_TEXT);
    }

    @Transactional
    public synchronized TransactionResponse confirmVoice(UUID workspaceId, QuickEntryConfirmRequest req, UUID userId) {
        rejectUnsupportedVoiceIntent(req);
        String sourceReference = voiceSourceReference(req);
        if (sourceReference != null) {
            var existing = transactionRepository.findSourceReferenceMatches(
                    workspaceId, userId, TransactionSourceType.VOICE, sourceReference);
            if (!existing.isEmpty()) {
                return transactionService.mapExistingToResponse(existing.get(0));
            }
        }
        return confirmWithSource(workspaceId, req, userId, TransactionSourceType.VOICE);
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
            if (req.getCategoryId() == null) {
                throw new BusinessException("QUICK_ENTRY_CATEGORY_NOT_FOUND", "Category is required");
            }
            txReq.setWalletId(req.getWalletId());
            txReq.setCategoryId(req.getCategoryId());
        } else {
            throw new BusinessException("INVALID_TRANSACTION_TYPE", "Invalid transaction type");
        }
        return txReq;
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

    private void rejectUnsupportedVoiceIntent(QuickEntryConfirmRequest req) {
        VoiceIntentType intentType = req == null ? null : req.getIntentType();
        if (intentType == null) {
            return;
        }
        if (intentType == VoiceIntentType.TRANSACTION_EXPENSE && req.getType() == TransactionType.EXPENSE) {
            return;
        }
        if (intentType == VoiceIntentType.TRANSACTION_INCOME && req.getType() == TransactionType.INCOME) {
            return;
        }
        if (intentType == VoiceIntentType.TRANSACTION_TRANSFER && req.getType() == TransactionType.TRANSFER) {
            return;
        }
        throw new BusinessException("VOICE_INTENT_NOT_COMMITTABLE", "Voice intent is not supported for commit");
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
