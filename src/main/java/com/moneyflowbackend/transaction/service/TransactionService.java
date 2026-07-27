package com.moneyflowbackend.transaction.service;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.transaction.audit.TransactionAuditAction;
import com.moneyflowbackend.transaction.audit.TransactionAuditService;
import com.moneyflowbackend.transaction.dto.TransactionPageResponse;
import com.moneyflowbackend.transaction.dto.TransactionRequest;
import com.moneyflowbackend.transaction.dto.TransactionResponse;
import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransactionSourceType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.model.TransferDetail;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.voice.model.VoiceRecord;
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
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransactionService {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";
    private static final int EXPORT_LIMIT = 5000;

    private static final Set<TransactionType> MANUAL_TYPES = Set.of(
            TransactionType.INCOME,
            TransactionType.EXPENSE,
            TransactionType.TRANSFER);
    private static final Set<TransactionStatus> MANUAL_STATUSES = Set.of(
            TransactionStatus.PLANNED,
            TransactionStatus.POSTED);
    private static final Set<String> SORT_FIELDS = Set.of(
            "transactionDate",
            "createdAt",
            "updatedAt",
            "amount");

    private final TransactionRepository transactionRepository;
    private final TransferDetailRepository transferDetailRepository;
    private final VoiceRecordRepository voiceRecordRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspacePersonRepository workspacePersonRepository;
    private final TransactionAuditService transactionAuditService;
    private final Clock clock;

    public TransactionService(
            TransactionRepository transactionRepository,
            TransferDetailRepository transferDetailRepository,
            VoiceRecordRepository voiceRecordRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            CategoryRepository categoryRepository,
            IncomeSourceRepository incomeSourceRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspacePersonRepository workspacePersonRepository,
            TransactionAuditService transactionAuditService,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.transferDetailRepository = transferDetailRepository;
        this.voiceRecordRepository = voiceRecordRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspacePersonRepository = workspacePersonRepository;
        this.transactionAuditService = transactionAuditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse list(
            UUID workspaceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TransactionType type,
            TransactionStatus status,
            UUID walletId,
            UUID categoryId,
            UUID attributedPersonId,
            TransactionSourceType sourceType,
            String search,
            boolean includeDeleted,
            int page,
            int size,
            String sort,
            UUID userId) {
        return list(workspaceId, dateFrom, dateTo, type, status, walletId, categoryId, null,
                attributedPersonId, sourceType, search, includeDeleted, page, size, sort, userId);
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse list(
            UUID workspaceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TransactionType type,
            TransactionStatus status,
            UUID walletId,
            UUID categoryId,
            UUID jarId,
            UUID attributedPersonId,
            TransactionSourceType sourceType,
            String search,
            boolean includeDeleted,
            int page,
            int size,
            String sort,
            UUID userId) {
        return list(workspaceId, dateFrom, dateTo, type, status, walletId, categoryId, jarId,
                attributedPersonId, sourceType, null, search, includeDeleted, page, size, sort, userId);
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse list(
            UUID workspaceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TransactionType type,
            TransactionStatus status,
            UUID walletId,
            UUID categoryId,
            UUID jarId,
            UUID attributedPersonId,
            TransactionSourceType sourceType,
            UUID createdBy,
            String search,
            boolean includeDeleted,
            int page,
            int size,
            String sort,
            UUID userId) {
        return list(workspaceId, dateFrom, dateTo, type, status, walletId, categoryId, jarId,
                attributedPersonId, sourceType, createdBy, null, search, includeDeleted, page, size, sort, userId);
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse list(
            UUID workspaceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TransactionType type,
            TransactionStatus status,
            UUID walletId,
            UUID categoryId,
            UUID jarId,
            UUID attributedPersonId,
            TransactionSourceType sourceType,
            UUID createdBy,
            SpendingScope spendingScope,
            String search,
            boolean includeDeleted,
            int page,
            int size,
            String sort,
            UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Invalid date range");
        }
        validateIncludeDeleted(includeDeleted, member);

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, parseSort(sort));
        Specification<Transaction> spec = buildListSpec(workspaceId, dateFrom, dateTo, type, status, walletId,
                categoryId, jarId, attributedPersonId, sourceType, createdBy, spendingScope, search, includeDeleted);

        Page<Transaction> result = transactionRepository.findAll(spec, pageable);
        return TransactionPageResponse.builder()
                .content(mapToResponses(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            UUID workspaceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TransactionType type,
            TransactionStatus status,
            UUID walletId,
            UUID categoryId,
            UUID jarId,
            UUID attributedPersonId,
            TransactionSourceType sourceType,
            UUID createdBy,
            String search,
            boolean includeDeleted,
            UUID userId) {
        return exportCsv(workspaceId, dateFrom, dateTo, type, status, walletId, categoryId, jarId,
                attributedPersonId, sourceType, createdBy, null, search, includeDeleted, userId);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            UUID workspaceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TransactionType type,
            TransactionStatus status,
            UUID walletId,
            UUID categoryId,
            UUID jarId,
            UUID attributedPersonId,
            TransactionSourceType sourceType,
            UUID createdBy,
            SpendingScope spendingScope,
            String search,
            boolean includeDeleted,
            UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Invalid date range");
        }
        validateIncludeDeleted(includeDeleted, member);

        Specification<Transaction> spec = buildListSpec(workspaceId, dateFrom, dateTo, type, status, walletId,
                categoryId, jarId, attributedPersonId, sourceType, createdBy, spendingScope, search, includeDeleted);
        List<Transaction> exportTransactions = transactionRepository
                .findAll(spec, PageRequest.of(0, EXPORT_LIMIT, parseSort(null)))
                .getContent();
        List<TransactionResponse> rows = mapToResponses(exportTransactions);

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("transactionDate,type,amount,walletName,transferSourceWalletName,transferDestinationWalletName,categoryName,jarName,sourceType,createdByUsername,note,rawInput,isDeleted,createdAt,updatedAt,spendingScope\n");
        for (TransactionResponse row : rows) {
            csv.append(csv(row.getTransactionDate()))
                    .append(',').append(csv(row.getType()))
                    .append(',').append(csv(row.getAmount()))
                    .append(',').append(csv(row.getWalletName()))
                    .append(',').append(csv(row.getSourceWalletName()))
                    .append(',').append(csv(row.getDestinationWalletName()))
                    .append(',').append(csv(row.getCategoryName()))
                    .append(',').append(csv(row.getCategory() == null ? null : row.getCategory().getJarName()))
                    .append(',').append(csv(row.getSourceType()))
                    .append(',').append(csv(row.getCreatedBy() == null ? null : row.getCreatedBy().getUsername()))
                    .append(',').append(csv(row.getNote()))
                    .append(',').append(csv(row.getRawInput()))
                    .append(',').append(csv(row.getDeletedAt() != null))
                    .append(',').append(csv(row.getCreatedAt()))
                    .append(',').append(csv(row.getUpdatedAt()))
                    .append(',').append(csv(row.getSpendingScope()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Specification<Transaction> buildListSpec(
            UUID workspaceId,
            LocalDate dateFrom,
            LocalDate dateTo,
            TransactionType type,
            TransactionStatus status,
            UUID walletId,
            UUID categoryId,
            UUID jarId,
            UUID attributedPersonId,
            TransactionSourceType sourceType,
            UUID createdBy,
            SpendingScope spendingScope,
            String search,
            boolean includeDeleted) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("workspace").get("id"), workspaceId));
            if (!includeDeleted) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), dateTo));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("transactionType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("transactionStatus"), status));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (jarId != null) {
                predicates.add(cb.equal(root.get("category").get("jar").get("id"), jarId));
            }
            if (attributedPersonId != null) {
                predicates.add(cb.equal(root.get("attributedPerson").get("id"), attributedPersonId));
            }
            if (sourceType != null) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            if (createdBy != null) {
                predicates.add(cb.equal(root.get("createdByUser").get("id"), createdBy));
            }
            if (spendingScope != null) {
                predicates.add(cb.equal(root.get("spendingScope"), spendingScope));
            }
            if (walletId != null) {
                Join<Transaction, Wallet> walletJoin = root.join("wallet", JoinType.LEFT);
                Subquery<UUID> transferSubquery = query.subquery(UUID.class);
                Root<TransferDetail> td = transferSubquery.from(TransferDetail.class);
                transferSubquery.select(td.get("transaction").get("id"));
                transferSubquery.where(
                        cb.equal(td.get("transaction").get("id"), root.get("id")),
                        cb.or(
                                cb.equal(td.get("sourceWallet").get("id"), walletId),
                                cb.equal(td.get("destinationWallet").get("id"), walletId)));
                predicates.add(cb.or(
                        cb.equal(walletJoin.get("id"), walletId),
                        cb.exists(transferSubquery)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("description")), pattern),
                        cb.like(cb.lower(root.<String>get("note")), pattern),
                        cb.like(cb.lower(root.<String>get("rawInput")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public TransactionResponse getDetails(UUID workspaceId, UUID transactionId, boolean includeDeleted, UUID userId) {
        requireActiveMember(workspaceId, userId);
        Transaction tx = findTransaction(workspaceId, transactionId);
        if (tx.getDeletedAt() != null && !includeDeleted) {
            throw new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND);
        }
        return mapToResponse(tx);
    }

    @Transactional
    public TransactionResponse create(UUID workspaceId, TransactionRequest req, UUID userId) {
        return createWithSource(workspaceId, req, userId, TransactionSourceType.MANUAL, null, null);
    }

    @Transactional
    public TransactionResponse createConfirmedObligationTransaction(
            UUID workspaceId,
            TransactionType type,
            BigDecimal amount,
            UUID walletId,
            UUID categoryId,
            LocalDate transactionDate,
            String description,
            String note,
            UUID userId) {
        return createConfirmedObligationTransaction(workspaceId, type, amount, walletId, categoryId, transactionDate, description, note, null, userId);
    }

    @Transactional
    public TransactionResponse createConfirmedObligationTransaction(
            UUID workspaceId,
            TransactionType type,
            BigDecimal amount,
            UUID walletId,
            UUID categoryId,
            LocalDate transactionDate,
            String description,
            String note,
            SpendingScope spendingScope,
            UUID userId) {
        requireWritableMember(workspaceId, userId);
        if (type != TransactionType.INCOME && type != TransactionType.EXPENSE) {
            throw new BusinessException("INVALID_TRANSACTION_TYPE", "Invalid obligation transaction type");
        }
        TransactionRequest req = new TransactionRequest();
        req.setType(type);
        req.setStatus(TransactionStatus.POSTED);
        req.setAmount(amount);
        req.setWalletId(walletId);
        req.setCategoryId(categoryId);
        req.setTransactionDate(transactionDate);
        req.setDescription(description);
        req.setNote(note);
        if (spendingScope != null) {
            req.setSpendingScope(spendingScope);
        }
        return createWithSource(workspaceId, req, userId, TransactionSourceType.SYSTEM, null);
    }

    @Transactional(readOnly = true)
    public Transaction reference(UUID workspaceId, UUID transactionId) {
        return findTransaction(workspaceId, transactionId);
    }

    @Transactional
    public TransactionResponse createHistoricalExcelMigration(UUID workspaceId, TransactionRequest req, UUID userId,
                                                             String migrationKey, String sourceReference, String rawInput) {
        requireWritableMember(workspaceId, userId);
        if (migrationKey == null || migrationKey.isBlank()) {
            throw new BusinessException("MIGRATION_KEY_REQUIRED", "Migration key is required");
        }
        var existing = transactionRepository.findByWorkspaceIdAndMigrationKey(workspaceId, migrationKey.trim());
        if (existing.isPresent()) {
            return mapToResponse(existing.get());
        }
        Workspace workspace = findWorkspace(workspaceId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        TransactionType type = requireManualType(req.getType());
        validateIncomeSourceLinks(type, req.getIncomeSourceId(), req.getRelatedIncomeSourceId());
        if (type != TransactionType.INCOME && type != TransactionType.EXPENSE) {
            throw new BusinessException("INVALID_MIGRATION_TRANSACTION_TYPE", "Historical migration supports income/expense only");
        }
        BigDecimal amount = requireAmount(req.getAmount());
        Category category = resolveCategory(workspaceId, req.getCategoryId(), type, true, true);
        WorkspacePerson person = resolvePersonForWrite(workspaceId, req.getAttributedPersonId(), false);

        Transaction tx = Transaction.builder()
                .workspace(workspace)
                .createdByUser(user)
                .attributedPerson(person)
                .wallet(null)
                .category(category)
                .transactionType(type)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(amount)
                .currency(workspaceCurrency(workspace))
                .transactionDate(req.getTransactionDate() != null ? req.getTransactionDate() : today(workspace))
                .transactionTime(req.getTransactionTime())
                .description(normalizeText(req.getDescription()))
                .note(normalizeText(req.getNote()))
                .sourceType(TransactionSourceType.EXCEL_MIGRATION)
                .rawInput(normalizeText(rawInput))
                .sourceReference(normalizeText(sourceReference))
                .migrationKey(migrationKey.trim())
                .walletUnknown(true)
                .historical(true)
                .affectsWalletBalance(false)
                .legacyLabel(normalizeText(req.getDescription()))
                .build();
        return mapToResponse(transactionRepository.save(tx));
    }

    @Transactional
    public TransactionResponse createWithSource(UUID workspaceId, TransactionRequest req, UUID userId, TransactionSourceType sourceType, String rawInput) {
        return createWithSource(workspaceId, req, userId, sourceType, rawInput, null, null);
    }

    @Transactional
    public TransactionResponse createWithSource(UUID workspaceId, TransactionRequest req, UUID userId, TransactionSourceType sourceType, String rawInput, UUID voiceRecordId) {
        return createWithSource(workspaceId, req, userId, sourceType, rawInput, voiceRecordId, null);
    }

    @Transactional
    public TransactionResponse createWithSource(UUID workspaceId, TransactionRequest req, UUID userId, TransactionSourceType sourceType, String rawInput, UUID voiceRecordId, String sourceReference) {
        requireWritableMember(workspaceId, userId);
        Workspace workspace = findWorkspace(workspaceId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        TransactionType type = requireManualType(req.getType());
        TransactionSourceType normalizedSourceType = normalizeSourceType(sourceType);
        IncomeSourceLink incomeSourceLink = resolveIncomeSourceLinkForCreate(workspaceId, type, req);
        TransactionStatus status = normalizeStatus(req.getStatus(), TransactionStatus.POSTED);
        BigDecimal amount = requireAmount(req.getAmount());
        WorkspacePerson person = resolvePersonForWrite(workspaceId, req.getAttributedPersonId(), true);

        Transaction tx = Transaction.builder()
                .workspace(workspace)
                .createdByUser(user)
                .attributedPerson(person)
                .transactionType(type)
                .incomeSource(incomeSourceLink.incomeSource())
                .relatedIncomeSource(incomeSourceLink.relatedIncomeSource())
                .transactionStatus(status)
                .amount(amount)
                .currency(workspaceCurrency(workspace))
                .transactionDate(req.getTransactionDate() != null ? req.getTransactionDate() : today(workspace))
                .transactionTime(req.getTransactionTime())
                .description(normalizeText(req.getDescription()))
                .note(normalizeText(req.getNote()))
                .sourceType(normalizedSourceType)
                .rawInput(normalizeText(rawInput))
                .sourceReference(normalizeText(sourceReference))
                .walletUnknown(false)
                .historical(false)
                .affectsWalletBalance(true)
                .build();
        if (voiceRecordId != null) {
            voiceRecordRepository.findByIdAndWorkspaceId(voiceRecordId, workspaceId)
                    .orElseThrow(() -> new BusinessException("VOICE_RECORD_NOT_FOUND", "Voice record not found", HttpStatus.NOT_FOUND));
            tx.setVoiceRecordId(voiceRecordId);
        }

        if (type == TransactionType.TRANSFER) {
            validateSpendingScope(type, req);
            if (req.getCategoryId() != null) {
                throw new BusinessException("TRANSFER_CATEGORY_NOT_ALLOWED", "Transfer cannot have category");
            }
            Wallet source = resolveWallet(workspaceId, req.getSourceWalletId(), true, status == TransactionStatus.POSTED, "TRANSFER_SOURCE_REQUIRED");
            Wallet destination = resolveWallet(workspaceId, req.getDestinationWalletId(), true, status == TransactionStatus.POSTED, "TRANSFER_DESTINATION_REQUIRED");
            validateDifferentWallets(source, destination);

            tx.setWallet(source);
            tx.setCategory(null);
            tx = transactionRepository.saveAndFlush(tx);
            transferDetailRepository.save(TransferDetail.builder()
                    .transactionId(tx.getId())
                    .transaction(tx)
                    .sourceWallet(source)
                    .destinationWallet(destination)
                    .build());
            transactionAuditService.record(tx, userId, TransactionAuditAction.CREATE, null, transactionAuditService.snapshot(tx));
            return mapToResponse(tx);
        }

        Wallet wallet = resolveWallet(workspaceId, req.getWalletId(), true, true, "WALLET_NOT_FOUND");
        Category category = resolveCategory(workspaceId, req.getCategoryId(), type, type != TransactionType.EXPENSE, true);
        tx.setSpendingScope(resolveSpendingScopeForCreate(type, normalizedSourceType, req, category));
        tx.setWallet(wallet);
        tx.setCategory(category);
        tx = transactionRepository.save(tx);
        transactionAuditService.record(tx, userId, TransactionAuditAction.CREATE, null, transactionAuditService.snapshot(tx));
        return mapToResponse(tx);
    }

    @Transactional(readOnly = true)
    public TransactionResponse mapExistingToResponse(Transaction tx) {
        return mapToResponse(tx);
    }

    @Transactional
    public Transaction createAdjustment(
            UUID workspaceId,
            Wallet wallet,
            AdjustmentDirection direction,
            BigDecimal amount,
            LocalDate transactionDate,
            String note,
            UUID userId,
            String sourceReference) {
        if (wallet == null || !wallet.getWorkspace().getId().equals(workspaceId)) {
            throw new BusinessException("WALLET_NOT_FOUND", "Wallet not found", HttpStatus.NOT_FOUND);
        }
        if (direction == null) {
            throw new BusinessException("INVALID_ADJUSTMENT_DIRECTION", "Adjustment direction is required");
        }
        BigDecimal normalizedAmount = requireAmount(amount);
        Workspace workspace = wallet.getWorkspace();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        Instant now = clock.instant();
        Transaction tx = Transaction.builder()
                .workspace(workspace)
                .createdByUser(user)
                .wallet(wallet)
                .transactionType(TransactionType.ADJUSTMENT)
                .adjustmentDirection(direction)
                .transactionStatus(TransactionStatus.POSTED)
                .amount(normalizedAmount)
                .currency(workspaceCurrency(workspace))
                .transactionDate(transactionDate)
                .description("Reconciliation adjustment")
                .note(normalizeText(note))
                .sourceType(TransactionSourceType.MANUAL)
                .sourceReference(normalizeText(sourceReference))
                .walletUnknown(false)
                .historical(false)
                .affectsWalletBalance(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        tx = transactionRepository.saveAndFlush(tx);
        transactionAuditService.record(tx, userId, TransactionAuditAction.CREATE, null, transactionAuditService.snapshot(tx));
        return tx;
    }

    @Transactional
    public TransactionResponse update(UUID workspaceId, UUID transactionId, TransactionRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Transaction tx = findTransaction(workspaceId, transactionId);
        if (tx.getDeletedAt() != null) {
            throw new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND);
        }
        var before = transactionAuditService.snapshot(tx);
        TransactionType requestedType = requireManualType(req.getType());
        if (requestedType != tx.getTransactionType()) {
            throw new BusinessException("TRANSACTION_TYPE_IMMUTABLE", "Transaction type cannot be changed");
        }
        IncomeSourceLink incomeSourceLink = resolveIncomeSourceLinkForUpdate(workspaceId, requestedType, req, tx);
        validateSpendingScope(requestedType, req);

        TransactionStatus oldStatus = tx.getTransactionStatus();
        TransactionStatus newStatus = normalizeStatus(req.getStatus(), oldStatus);
        BigDecimal amount = requireAmount(req.getAmount());
        boolean postingNow = oldStatus != TransactionStatus.POSTED && newStatus == TransactionStatus.POSTED;

        tx.setTransactionStatus(newStatus);
        tx.setAmount(amount);
        if (req.getTransactionDate() != null) {
            tx.setTransactionDate(req.getTransactionDate());
        }
        tx.setTransactionTime(req.getTransactionTime());
        tx.setDescription(normalizeText(req.getDescription()));
        tx.setNote(normalizeText(req.getNote()));
        tx.setIncomeSource(incomeSourceLink.incomeSource());
        tx.setRelatedIncomeSource(incomeSourceLink.relatedIncomeSource());
        tx.setUpdatedAt(Instant.now());
        tx.setAttributedPerson(resolvePersonForUpdate(workspaceId, tx.getAttributedPerson(), req.getAttributedPersonId()));

        if (tx.getTransactionType() == TransactionType.TRANSFER) {
            if (req.getCategoryId() != null) {
                throw new BusinessException("TRANSFER_CATEGORY_NOT_ALLOWED", "Transfer cannot have category");
            }
            TransferDetail detail = transferDetailRepository.findById(tx.getId())
                    .orElseThrow(() -> new BusinessException("TRANSFER_DETAIL_NOT_FOUND", "Transfer detail not found"));
            Wallet source = resolveWalletForUpdate(workspaceId, detail.getSourceWallet(), req.getSourceWalletId(), true, newStatus, postingNow);
            Wallet destination = resolveWalletForUpdate(workspaceId, detail.getDestinationWallet(), req.getDestinationWalletId(), true, newStatus, postingNow);
            validateDifferentWallets(source, destination);
            tx.setWallet(source);
            tx.setCategory(null);
            tx.setSpendingScope(null);
            transactionRepository.save(tx);
            detail.setSourceWallet(source);
            detail.setDestinationWallet(destination);
            transferDetailRepository.save(detail);
            transactionAuditService.record(tx, userId, TransactionAuditAction.UPDATE, before, transactionAuditService.snapshot(tx));
            return mapToResponse(tx);
        }

        Wallet wallet = resolveWalletForUpdate(workspaceId, tx.getWallet(), req.getWalletId(), true, newStatus, postingNow);
        Category category = resolveCategoryForUpdate(workspaceId, tx.getCategory(), req.getCategoryId(), tx.getTransactionType(), true, newStatus, postingNow);
        tx.setWallet(wallet);
        tx.setCategory(category);
        tx.setSpendingScope(resolveSpendingScopeForUpdate(requestedType, req, tx));
        tx = transactionRepository.save(tx);
        transactionAuditService.record(tx, userId, TransactionAuditAction.UPDATE, before, transactionAuditService.snapshot(tx));
        return mapToResponse(tx);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID transactionId, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Transaction tx = findTransaction(workspaceId, transactionId);
        if (tx.getDeletedAt() != null) {
            return;
        }
        var before = transactionAuditService.snapshot(tx);
        Instant now = Instant.now();
        tx.setDeletedAt(now);
        tx.setUpdatedAt(now);
        tx = transactionRepository.save(tx);
        transactionAuditService.record(tx, userId, TransactionAuditAction.DELETE, before, transactionAuditService.snapshot(tx));
    }

    @Transactional
    public TransactionResponse restore(UUID workspaceId, UUID transactionId, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Transaction tx = findTransaction(workspaceId, transactionId);
        if (tx.getDeletedAt() == null) {
            throw new BusinessException("TRANSACTION_NOT_DELETED", "Transaction is not deleted");
        }
        var before = transactionAuditService.snapshot(tx);
        validateReferencesUsableForRestore(workspaceId, tx);
        if (tx.getTransactionType() == TransactionType.TRANSFER) {
            TransferDetail detail = transferDetailRepository.findById(tx.getId())
                    .orElseThrow(() -> new BusinessException("TRANSFER_DETAIL_NOT_FOUND", "Transfer detail not found"));
            if (tx.isAffectsWalletBalance() && (!detail.getSourceWallet().isActive() || !detail.getDestinationWallet().isActive())) {
                throw new BusinessException("WALLET_INACTIVE", "Wallet is inactive");
            }
        }
        tx.setDeletedAt(null);
        tx.setUpdatedAt(Instant.now());
        tx = transactionRepository.save(tx);
        transactionAuditService.record(tx, userId, TransactionAuditAction.RESTORE, before, transactionAuditService.snapshot(tx));
        return mapToResponse(tx);
    }

    private Workspace findWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
    }

    private Transaction findTransaction(UUID workspaceId, UUID transactionId) {
        return transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        findWorkspace(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify transactions", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private void validateIncludeDeleted(boolean includeDeleted, WorkspaceMember member) {
        if (includeDeleted && member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot include deleted transactions", HttpStatus.FORBIDDEN);
        }
    }

    private TransactionType requireManualType(TransactionType type) {
        if (type == null || !MANUAL_TYPES.contains(type)) {
            throw new BusinessException("INVALID_TRANSACTION_TYPE", "Invalid transaction type");
        }
        return type;
    }

    private TransactionStatus normalizeStatus(TransactionStatus status, TransactionStatus fallback) {
        TransactionStatus normalized = status == null ? fallback : status;
        if (!MANUAL_STATUSES.contains(normalized)) {
            throw new BusinessException("INVALID_TRANSACTION_STATUS", "Invalid transaction status");
        }
        return normalized;
    }

    private TransactionSourceType normalizeSourceType(TransactionSourceType sourceType) {
        if (sourceType == null) {
            return TransactionSourceType.MANUAL;
        }
        return sourceType;
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Amount must be greater than 0");
        }
        return amount;
    }

    private LocalDate today(Workspace workspace) {
        try {
            String timezone = workspace.getTimezone() == null ? FALLBACK_ZONE : workspace.getTimezone();
            return LocalDate.now(clock.withZone(ZoneId.of(timezone)));
        } catch (DateTimeException ex) {
            return LocalDate.now(clock.withZone(ZoneId.of(FALLBACK_ZONE)));
        }
    }

    private Wallet resolveWallet(UUID workspaceId, UUID walletId, boolean required, boolean requireActive, String missingCode) {
        if (walletId == null) {
            if (required) {
                throw new BusinessException(missingCode, "Wallet is required");
            }
            return null;
        }
        Wallet wallet = walletRepository.findByIdAndWorkspaceId(walletId, workspaceId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found", HttpStatus.NOT_FOUND));
        if (requireActive && !wallet.isActive()) {
            throw new BusinessException("WALLET_INACTIVE", "Wallet is inactive");
        }
        return wallet;
    }

    private Wallet resolveWalletForUpdate(UUID workspaceId, Wallet current, UUID requestedId, boolean required, TransactionStatus status, boolean postingNow) {
        if (requestedId == null) {
            if (required) {
                throw new BusinessException("WALLET_NOT_FOUND", "Wallet is required");
            }
            return null;
        }
        boolean changed = current == null || !current.getId().equals(requestedId);
        return resolveWallet(workspaceId, requestedId, required, status == TransactionStatus.POSTED && (changed || postingNow), "WALLET_NOT_FOUND");
    }

    private Category resolveCategory(UUID workspaceId, UUID categoryId, TransactionType type, boolean required, boolean requireActive) {
        if (categoryId == null) {
            if (required) {
                throw new BusinessException("CATEGORY_NOT_FOUND", "Category is required");
            }
            return null;
        }
        Category category = categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
        if (requireActive) {
            if (!category.isActive()) {
                throw new BusinessException("CATEGORY_INACTIVE", "Category is inactive");
            }
            if (category.isArchived()) {
                throw new BusinessException("CATEGORY_ARCHIVED", "Category is archived");
            }
        }
        validateCategoryType(type, category);
        return category;
    }

    private Category resolveCategoryForUpdate(UUID workspaceId, Category current, UUID requestedId, TransactionType type, boolean required, TransactionStatus status, boolean postingNow) {
        if (requestedId == null) {
            if (required) {
                throw new BusinessException("CATEGORY_NOT_FOUND", "Category is required");
            }
            return null;
        }
        boolean changed = current == null || !current.getId().equals(requestedId);
        return resolveCategory(workspaceId, requestedId, type, required, status == TransactionStatus.POSTED && (changed || postingNow));
    }

    private SpendingScope resolveSpendingScopeForCreate(TransactionType type, TransactionSourceType sourceType, TransactionRequest req, Category category) {
        validateSpendingScope(type, req);
        if (type != TransactionType.EXPENSE) {
            return null;
        }
        if (req.hasSpendingScope()) {
            return req.getSpendingScope();
        }
        return category == null ? null : category.getDefaultSpendingScope();
    }

    private SpendingScope resolveSpendingScopeForUpdate(TransactionType type, TransactionRequest req, Transaction tx) {
        if (type != TransactionType.EXPENSE) {
            return null;
        }
        return req.hasSpendingScope() ? req.getSpendingScope() : tx.getSpendingScope();
    }

    private void validateSpendingScope(TransactionType type, TransactionRequest req) {
        if (type != TransactionType.EXPENSE && req.hasSpendingScope() && req.getSpendingScope() != null) {
            throw new BusinessException(
                    "INVALID_TRANSACTION_SPENDING_SCOPE",
                    "Spending scope is only supported for expense transactions.");
        }
    }

    private IncomeSourceLink resolveIncomeSourceLinkForCreate(UUID workspaceId, TransactionType type, TransactionRequest req) {
        validateIncomeSourceLinks(type, req.getIncomeSourceId(), req.getRelatedIncomeSourceId());
        return switch (type) {
            case INCOME -> new IncomeSourceLink(resolveActiveIncomeSource(workspaceId, req.getIncomeSourceId(), null), null);
            case EXPENSE -> new IncomeSourceLink(null, resolveActiveIncomeSource(workspaceId, req.getRelatedIncomeSourceId(), null));
            default -> new IncomeSourceLink(null, null);
        };
    }

    private IncomeSourceLink resolveIncomeSourceLinkForUpdate(UUID workspaceId, TransactionType type, TransactionRequest req, Transaction tx) {
        UUID incomeSourceId = req.hasIncomeSourceId()
                ? req.getIncomeSourceId()
                : tx.getIncomeSource() == null ? null : tx.getIncomeSource().getId();
        UUID relatedIncomeSourceId = req.hasRelatedIncomeSourceId()
                ? req.getRelatedIncomeSourceId()
                : tx.getRelatedIncomeSource() == null ? null : tx.getRelatedIncomeSource().getId();
        validateIncomeSourceLinks(type, incomeSourceId, relatedIncomeSourceId);
        return switch (type) {
            case INCOME -> new IncomeSourceLink(resolveActiveIncomeSource(workspaceId, incomeSourceId, tx.getIncomeSource()), null);
            case EXPENSE -> new IncomeSourceLink(null, resolveActiveIncomeSource(workspaceId, relatedIncomeSourceId, tx.getRelatedIncomeSource()));
            default -> new IncomeSourceLink(null, null);
        };
    }

    private void validateIncomeSourceLinks(TransactionType type, UUID incomeSourceId, UUID relatedIncomeSourceId) {
        if (incomeSourceId != null && relatedIncomeSourceId != null) {
            throw invalidIncomeSourceLink();
        }
        if (type == TransactionType.INCOME && relatedIncomeSourceId != null) {
            throw invalidIncomeSourceLink();
        }
        if (type == TransactionType.EXPENSE && incomeSourceId != null) {
            throw invalidIncomeSourceLink();
        }
        if (type != TransactionType.INCOME && type != TransactionType.EXPENSE
                && (incomeSourceId != null || relatedIncomeSourceId != null)) {
            throw invalidIncomeSourceLink();
        }
    }

    private IncomeSource resolveActiveIncomeSource(UUID workspaceId, UUID requestedId, IncomeSource current) {
        if (requestedId == null) {
            return null;
        }
        IncomeSource source = incomeSourceRepository.findByIdAndWorkspaceId(requestedId, workspaceId)
                .orElseThrow(() -> new BusinessException("INCOME_SOURCE_NOT_FOUND", "Income source not found", HttpStatus.NOT_FOUND));
        if (source.getStatus() == IncomeSourceStatus.ACTIVE) {
            return source;
        }
        if (current != null && current.getId().equals(requestedId)) {
            return source;
        }
        throw new BusinessException("INCOME_SOURCE_ARCHIVED", "Income source is archived", HttpStatus.CONFLICT);
    }

    private BusinessException invalidIncomeSourceLink() {
        return new BusinessException("INVALID_INCOME_SOURCE_LINK", "Income source link is invalid");
    }

    private WorkspacePerson resolvePersonForWrite(UUID workspaceId, UUID personId, boolean requireActive) {
        if (personId == null) {
            return null;
        }
        WorkspacePerson person = workspacePersonRepository.findByIdAndWorkspaceId(personId, workspaceId)
                .orElseThrow(() -> new BusinessException("PERSON_NOT_FOUND", "Person not found", HttpStatus.NOT_FOUND));
        if (requireActive && !person.isActive()) {
            throw new BusinessException("PERSON_INACTIVE", "Person is inactive");
        }
        return person;
    }

    private WorkspacePerson resolvePersonForUpdate(UUID workspaceId, WorkspacePerson current, UUID requestedId) {
        if (requestedId == null) {
            return null;
        }
        boolean changed = current == null || !current.getId().equals(requestedId);
        return resolvePersonForWrite(workspaceId, requestedId, changed);
    }

    private void validateCategoryType(TransactionType type, Category category) {
        if (type == TransactionType.INCOME && category.getCategoryType() != CategoryType.INCOME) {
            throw new BusinessException("CATEGORY_TYPE_MISMATCH", "Category type mismatch");
        }
        if (type == TransactionType.EXPENSE && category.getCategoryType() != CategoryType.EXPENSE) {
            throw new BusinessException("CATEGORY_TYPE_MISMATCH", "Category type mismatch");
        }
    }

    private void validateDifferentWallets(Wallet source, Wallet destination) {
        if (source == null) {
            throw new BusinessException("TRANSFER_SOURCE_REQUIRED", "Transfer source is required");
        }
        if (destination == null) {
            throw new BusinessException("TRANSFER_DESTINATION_REQUIRED", "Transfer destination is required");
        }
        if (source.getId().equals(destination.getId())) {
            throw new BusinessException("TRANSFER_SAME_WALLET", "Transfer wallets must be different");
        }
    }

    private void validateReferencesUsableForRestore(UUID workspaceId, Transaction tx) {
        if (tx.getWallet() != null) {
            Wallet wallet = walletRepository.findByIdAndWorkspaceId(tx.getWallet().getId(), workspaceId)
                    .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found", HttpStatus.NOT_FOUND));
            if (tx.isAffectsWalletBalance() && !wallet.isActive()) {
                throw new BusinessException("WALLET_INACTIVE", "Wallet is inactive");
            }
        }
        if (tx.getCategory() != null) {
            Category category = categoryRepository.findByIdAndWorkspaceId(tx.getCategory().getId(), workspaceId)
                    .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
            if (!category.isActive()) {
                throw new BusinessException("CATEGORY_INACTIVE", "Category is inactive");
            }
            if (category.isArchived()) {
                throw new BusinessException("CATEGORY_ARCHIVED", "Category is archived");
            }
            validateCategoryType(tx.getTransactionType(), category);
        }
        if (tx.getAttributedPerson() != null) {
            workspacePersonRepository.findByIdAndWorkspaceId(tx.getAttributedPerson().getId(), workspaceId)
                    .orElseThrow(() -> new BusinessException("PERSON_NOT_FOUND", "Person not found", HttpStatus.NOT_FOUND));
        }
        if (tx.getVoiceRecordId() != null) {
            voiceRecordRepository.findByIdAndWorkspaceId(tx.getVoiceRecordId(), workspaceId)
                    .orElseThrow(() -> new BusinessException("VOICE_RECORD_NOT_FOUND", "Voice record not found", HttpStatus.NOT_FOUND));
        }
    }

    private Sort parseSort(String sort) {
        Sort fallback = Sort.by(
                Sort.Order.desc("transactionDate"),
                Sort.Order.desc("createdAt"));
        if (sort == null || sort.isBlank()) {
            return fallback;
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String token : sort.split(";")) {
            String[] parts = token.split(",");
            String field = sortField(parts[0].trim());
            if (!SORT_FIELDS.contains(field)) {
                continue;
            }
            boolean desc = parts.length < 2 || !"asc".equalsIgnoreCase(parts[1].trim());
            orders.add(desc ? Sort.Order.desc(field) : Sort.Order.asc(field));
        }
        return orders.isEmpty() ? fallback : Sort.by(orders);
    }

    private String sortField(String field) {
        return "date".equals(field) ? "transactionDate" : field;
    }

    private String workspaceCurrency(Workspace workspace) {
        return workspace.getCurrency() == null ? "VND" : workspace.getCurrency().trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private List<TransactionResponse> mapToResponses(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }
        List<UUID> transferIds = transactions.stream()
                .filter(tx -> tx.getTransactionType() == TransactionType.TRANSFER)
                .map(Transaction::getId)
                .toList();
        Map<UUID, TransferDetail> transferDetails = transferIds.isEmpty()
                ? Map.of()
                : transferDetailRepository.findAllWithWalletsByTransactionIdIn(transferIds).stream()
                .collect(Collectors.toMap(TransferDetail::getTransactionId, Function.identity()));

        List<UUID> voiceIds = transactions.stream()
                .map(Transaction::getVoiceRecordId)
                .filter(id -> id != null)
                .toList();
        Map<UUID, VoiceRecord> voiceRecords = voiceIds.isEmpty()
                ? Map.of()
                : voiceRecordRepository.findAllById(voiceIds).stream()
                .collect(Collectors.toMap(VoiceRecord::getId, Function.identity()));

        return transactions.stream()
                .map(tx -> mapToResponse(tx, transferDetails, voiceRecords))
                .toList();
    }

    public TransactionResponse mapToResponse(Transaction tx) {
        Map<UUID, TransferDetail> transferDetails = tx.getTransactionType() == TransactionType.TRANSFER
                ? transferDetailRepository.findById(tx.getId()).map(td -> Map.of(tx.getId(), td)).orElseGet(Map::of)
                : Map.of();
        Map<UUID, VoiceRecord> voiceRecords = tx.getVoiceRecordId() == null
                ? Map.of()
                : voiceRecordRepository.findById(tx.getVoiceRecordId()).map(vr -> Map.of(tx.getVoiceRecordId(), vr)).orElseGet(Map::of);
        return mapToResponse(tx, transferDetails, voiceRecords);
    }

    private TransactionResponse mapToResponse(
            Transaction tx,
            Map<UUID, TransferDetail> transferDetails,
            Map<UUID, VoiceRecord> voiceRecords) {
        TransactionResponse.TransactionResponseBuilder builder = TransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getTransactionType().name())
                .status(tx.getTransactionStatus().name())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .transactionDate(tx.getTransactionDate())
                .transactionTime(tx.getTransactionTime())
                .description(tx.getDescription())
                .note(tx.getNote())
                .rawInput(tx.getRawInput())
                .sourceType(tx.getSourceType().name())
                .voiceRecordId(tx.getVoiceRecordId())
                .hasVoiceAudio(false)
                .voiceAudioAvailable(false)
                .playbackAvailable(false)
                .audioUploadedAt(null)
                .voiceAudioStatus(null)
                .historical(tx.isHistorical())
                .affectsWalletBalance(tx.isAffectsWalletBalance())
                .walletUnknown(tx.isWalletUnknown())
                .spendingScope(tx.getSpendingScope())
                .incomeSourceId(tx.getIncomeSource() == null ? null : tx.getIncomeSource().getId())
                .relatedIncomeSourceId(tx.getRelatedIncomeSource() == null ? null : tx.getRelatedIncomeSource().getId())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .deletedAt(tx.getDeletedAt());

        if (tx.getVoiceRecordId() != null) {
            VoiceRecord voiceRecord = voiceRecords.get(tx.getVoiceRecordId());
            if (voiceRecord != null) {
                boolean hasAudio = voiceRecord.getAudioStorageKey() != null
                        || voiceRecord.getStorageKey() != null
                        || voiceRecord.getStoragePublicId() != null;
                builder.hasVoiceAudio(hasAudio);
                builder.voiceAudioAvailable(hasAudio);
                builder.playbackAvailable(hasAudio);
                builder.audioMimeType(voiceRecord.getMimeType());
                builder.audioSizeBytes(voiceRecord.getFileSizeBytes());
                builder.audioUploadedAt(voiceRecord.getAudioUploadedAt());
                builder.voiceAudioStatus(voiceRecord.getVoiceStatus().name());
            }
        }

        if (tx.getCreatedByUser() != null) {
            builder.createdBy(TransactionResponse.UserRef.builder()
                    .id(tx.getCreatedByUser().getId())
                    .username(tx.getCreatedByUser().getUsername())
                    .fullName(tx.getCreatedByUser().getFullName())
                    .build());
        }
        if (tx.getAttributedPerson() != null) {
            builder.attributedPerson(TransactionResponse.PersonRef.builder()
                    .id(tx.getAttributedPerson().getId())
                    .displayName(tx.getAttributedPerson().getDisplayName())
                    .build());
        }

        if (tx.getTransactionType() == TransactionType.TRANSFER) {
            TransferDetail td = transferDetails.get(tx.getId());
            if (td != null) {
                builder.sourceWalletId(td.getSourceWallet().getId());
                builder.sourceWalletName(td.getSourceWallet().getName());
                builder.destinationWalletId(td.getDestinationWallet().getId());
                builder.destinationWalletName(td.getDestinationWallet().getName());
                builder.transfer(TransactionResponse.TransferRef.builder()
                        .sourceWalletId(td.getSourceWallet().getId())
                        .sourceWalletName(td.getSourceWallet().getName())
                        .destinationWalletId(td.getDestinationWallet().getId())
                        .destinationWalletName(td.getDestinationWallet().getName())
                        .build());
            }
            return builder.build();
        }

        if (tx.getWallet() != null) {
            builder.walletId(tx.getWallet().getId());
            builder.walletName(tx.getWallet().getName());
            builder.wallet(TransactionResponse.WalletRef.builder()
                    .id(tx.getWallet().getId())
                    .name(tx.getWallet().getName())
                    .type(tx.getWallet().getWalletType().name())
                    .build());
        }
        if (tx.getCategory() != null) {
            builder.categoryId(tx.getCategory().getId());
            builder.categoryName(tx.getCategory().getName());
            builder.category(TransactionResponse.CategoryRef.builder()
                    .id(tx.getCategory().getId())
                    .name(tx.getCategory().getName())
                    .type(tx.getCategory().getCategoryType().name())
                    .jarId(tx.getCategory().getJar() != null ? tx.getCategory().getJar().getId() : null)
                    .jarName(tx.getCategory().getJar() != null ? tx.getCategory().getJar().getName() : null)
                    .build());
        }
        return builder.build();
    }

    private record IncomeSourceLink(IncomeSource incomeSource, IncomeSource relatedIncomeSource) {
    }
}





