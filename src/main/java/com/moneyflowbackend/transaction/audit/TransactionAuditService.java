package com.moneyflowbackend.transaction.audit;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.transaction.audit.dto.TransactionAuditResponse;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.model.TransferDetail;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.transaction.repository.TransferDetailRepository;
import com.moneyflowbackend.voice.repository.VoiceRecordRepository;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.repository.WorkspacePersonRepository;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TransactionAuditService {
    private final TransactionAuditLogRepository auditLogRepository;
    private final TransactionRepository transactionRepository;
    private final TransferDetailRepository transferDetailRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final WorkspacePersonRepository workspacePersonRepository;
    private final VoiceRecordRepository voiceRecordRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public TransactionAuditService(
            TransactionAuditLogRepository auditLogRepository,
            TransactionRepository transactionRepository,
            TransferDetailRepository transferDetailRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            CategoryRepository categoryRepository,
            IncomeSourceRepository incomeSourceRepository,
            WorkspacePersonRepository workspacePersonRepository,
            VoiceRecordRepository voiceRecordRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.auditLogRepository = auditLogRepository;
        this.transactionRepository = transactionRepository;
        this.transferDetailRepository = transferDetailRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.workspacePersonRepository = workspacePersonRepository;
        this.voiceRecordRepository = voiceRecordRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionAuditResponse> list(UUID workspaceId, UUID transactionId, UUID userId) {
        requireOwner(workspaceId, userId);
        transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND));
        return auditLogRepository.findByWorkspaceIdAndTransactionIdOrderByCreatedAtAsc(workspaceId, transactionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionAuditResponse> listByTransactionId(UUID transactionId, UUID userId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND));
        return list(tx.getWorkspace().getId(), transactionId, userId);
    }

    public void record(Transaction tx, UUID actorUserId, TransactionAuditAction action,
                       Map<String, Object> before, Map<String, Object> after) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        auditLogRepository.save(TransactionAuditLog.builder()
                .workspace(tx.getWorkspace())
                .transaction(tx)
                .actorUser(actor)
                .action(action)
                .beforeData(before)
                .afterData(after)
                .build());
    }

    public Map<String, Object> snapshot(Transaction tx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", tx.getTransactionType().name());
        data.put("adjustmentDirection", tx.getAdjustmentDirection() != null ? tx.getAdjustmentDirection().name() : null);
        data.put("status", tx.getTransactionStatus().name());
        data.put("amount", tx.getAmount());
        data.put("currency", tx.getCurrency());
        data.put("transactionDate", tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null);
        data.put("transactionTime", tx.getTransactionTime() != null ? tx.getTransactionTime().toString() : null);
        data.put("description", tx.getDescription());
        data.put("note", tx.getNote());
        data.put("sourceType", tx.getSourceType().name());
        data.put("sourceReference", tx.getSourceReference());
        data.put("rawInput", tx.getRawInput());
        data.put("walletId", tx.getWallet() != null ? tx.getWallet().getId() : null);
        data.put("walletName", tx.getWallet() != null ? tx.getWallet().getName() : null);
        data.put("categoryId", tx.getCategory() != null ? tx.getCategory().getId() : null);
        data.put("categoryName", tx.getCategory() != null ? tx.getCategory().getName() : null);
        data.put("spendingScope", tx.getSpendingScope() != null ? tx.getSpendingScope().name() : null);
        data.put("incomeSourceId", tx.getIncomeSource() != null ? tx.getIncomeSource().getId() : null);
        data.put("relatedIncomeSourceId", tx.getRelatedIncomeSource() != null ? tx.getRelatedIncomeSource().getId() : null);
        data.put("attributedPersonId", tx.getAttributedPerson() != null ? tx.getAttributedPerson().getId() : null);
        data.put("attributedPersonName", tx.getAttributedPerson() != null ? tx.getAttributedPerson().getDisplayName() : null);
        data.put("voiceRecordId", tx.getVoiceRecordId());
        data.put("voiceSessionId", tx.getVoiceSessionId());
        data.put("voiceSessionDraftId", tx.getVoiceSessionDraftId());
        data.put("deletedAt", tx.getDeletedAt() != null ? tx.getDeletedAt().toString() : null);
        if (tx.getTransactionType().name().equals("TRANSFER")) {
            transferDetailRepository.findById(tx.getId()).ifPresent(detail -> addTransfer(data, detail));
        }
        return data;
    }

    private void addTransfer(Map<String, Object> data, TransferDetail detail) {
        data.put("sourceWalletId", detail.getSourceWallet().getId());
        data.put("sourceWalletName", detail.getSourceWallet().getName());
        data.put("destinationWalletId", detail.getDestinationWallet().getId());
        data.put("destinationWalletName", detail.getDestinationWallet().getName());
    }

    private WorkspaceMember requireOwner(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new BusinessException("FORBIDDEN", "Only workspace owner can view transaction audit", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private TransactionAuditResponse toResponse(TransactionAuditLog log) {
        User actor = log.getActorUser();
        String action = log.getAction().name();
        UUID workspaceId = log.getWorkspace().getId();
        Map<String, Object> before = log.getBeforeData();
        Map<String, Object> after = log.getAfterData();
        String sourceType = sourceType(before, after);
        return TransactionAuditResponse.builder()
                .id(log.getId())
                .transactionId(log.getTransaction() != null ? log.getTransaction().getId() : null)
                .workspaceId(workspaceId)
                .action(action)
                .actionLabel(actionLabel(action, sourceType))
                .actorId(actor == null ? null : actor.getId())
                .actorName(actorName(actor))
                .actor(actor == null ? null : TransactionAuditResponse.Actor.builder()
                        .id(actor.getId())
                        .username(actor.getUsername())
                        .displayName(actor.getFullName())
                        .build())
                .sourceType(sourceType)
                .sourceLabel(sourceLabel(sourceType))
                .summary(actionLabel(action, sourceType))
                .changes(changes(workspaceId, before, after))
                .context(context(workspaceId, before, after, log.getTransaction()))
                .technicalPayload(Map.of("before", before == null ? Map.of() : before, "after", after == null ? Map.of() : after))
                .before(before)
                .after(after)
                .occurredAt(log.getCreatedAt())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private List<TransactionAuditResponse.Change> changes(UUID workspaceId, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (before != null) {
            before.keySet().forEach(key -> fields.put(key, null));
        }
        if (after != null) {
            after.keySet().forEach(key -> fields.put(key, null));
        }
        return fields.keySet().stream()
                .filter(field -> !displayOnlyField(field))
                .map(field -> change(workspaceId, field, value(before, field), value(after, field), before, after))
                .filter(Objects::nonNull)
                .toList();
    }

    private TransactionAuditResponse.Change change(
            UUID workspaceId,
            String field,
            Object beforeRaw,
            Object afterRaw,
            Map<String, Object> before,
            Map<String, Object> after) {
        if (Objects.equals(beforeRaw, afterRaw) || (beforeRaw == null && afterRaw == null)) {
            return null;
        }
        boolean technical = technicalField(field);
        return TransactionAuditResponse.Change.builder()
                .field(field)
                .fieldLabel(fieldLabel(field))
                .beforeRaw(beforeRaw)
                .afterRaw(afterRaw)
                .beforeDisplay(displayValue(workspaceId, field, beforeRaw, before))
                .afterDisplay(displayValue(workspaceId, field, afterRaw, after))
                .valueType(valueType(field))
                .technical(technical)
                .important(importantField(field))
                .build();
    }

    private TransactionAuditResponse.Context context(
            UUID workspaceId,
            Map<String, Object> before,
            Map<String, Object> after,
            Transaction transaction) {
        UUID voiceRecordId = uuid(value(after, "voiceRecordId"));
        if (voiceRecordId == null) {
            voiceRecordId = uuid(value(before, "voiceRecordId"));
        }
        String sourceReference = stringValue(value(after, "sourceReference"));
        if (sourceReference == null && transaction != null) {
            sourceReference = transaction.getSourceReference();
        }
        return TransactionAuditResponse.Context.builder()
                .voiceRecordId(voiceRecordId)
                .voiceTranscript(voiceTranscript(workspaceId, voiceRecordId))
                .importSource("EXCEL_MIGRATION".equals(sourceType(before, after)) ? "Excel" : null)
                .sourceReference(sourceReference)
                .relatedEntityType("TRANSACTION")
                .relatedEntityId(transaction == null ? null : transaction.getId())
                .relatedEntityLabel(transaction == null ? null : transactionLabel(transaction))
                .build();
    }

    private String sourceType(Map<String, Object> before, Map<String, Object> after) {
        String sourceType = stringValue(value(after, "sourceType"));
        return sourceType != null ? sourceType : stringValue(value(before, "sourceType"));
    }

    private Object value(Map<String, Object> data, String field) {
        return data == null ? null : data.get(field);
    }

    private String displayValue(UUID workspaceId, String field, Object raw, Map<String, Object> snapshot) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString();
        return switch (field) {
            case "type" -> typeLabel(value);
            case "sourceType" -> sourceLabel(value);
            case "spendingScope" -> spendingScopeLabel(value);
            case "adjustmentDirection" -> adjustmentDirectionLabel(value);
            case "walletId" -> displayName(snapshot, "walletName", value, id -> walletRepository.findByIdAndWorkspaceId(id, workspaceId).map(w -> w.getName()));
            case "categoryId" -> displayName(snapshot, "categoryName", value, id -> categoryRepository.findByIdAndWorkspaceId(id, workspaceId).map(c -> c.getName()));
            case "incomeSourceId", "relatedIncomeSourceId" -> displayName(snapshot, null, value, id -> incomeSourceRepository.findByIdAndWorkspaceId(id, workspaceId).map(s -> s.getName()));
            case "attributedPersonId" -> displayName(snapshot, "attributedPersonName", value, id -> workspacePersonRepository.findByIdAndWorkspaceId(id, workspaceId).map(p -> p.getDisplayName()));
            case "sourceWalletId" -> displayName(snapshot, "sourceWalletName", value, id -> walletRepository.findByIdAndWorkspaceId(id, workspaceId).map(w -> w.getName()));
            case "destinationWalletId" -> displayName(snapshot, "destinationWalletName", value, id -> walletRepository.findByIdAndWorkspaceId(id, workspaceId).map(w -> w.getName()));
            case "deletedAt" -> "Đã xóa";
            default -> value;
        };
    }

    private String displayName(Map<String, Object> snapshot, String nameField, String fallback, java.util.function.Function<UUID, java.util.Optional<String>> resolver) {
        String name = nameField == null ? null : stringValue(value(snapshot, nameField));
        if (name != null) {
            return name;
        }
        UUID id = uuid(fallback);
        return id == null ? fallback : resolver.apply(id).orElse("Không tìm thấy");
    }

    private UUID uuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String actorName(User actor) {
        if (actor == null) {
            return null;
        }
        return actor.getFullName() != null ? actor.getFullName() : actor.getUsername();
    }

    private String voiceTranscript(UUID workspaceId, UUID voiceRecordId) {
        if (voiceRecordId == null) {
            return null;
        }
        return voiceRecordRepository.findByIdAndWorkspaceId(voiceRecordId, workspaceId)
                .map(vr -> vr.getEditedTranscript() != null ? vr.getEditedTranscript() : vr.getOriginalTranscript())
                .orElse(null);
    }

    private String transactionLabel(Transaction tx) {
        return (tx.getDescription() != null && !tx.getDescription().isBlank())
                ? tx.getDescription()
                : typeLabel(tx.getTransactionType().name());
    }

    private String actionLabel(String action, String sourceType) {
        if ("CREATE".equals(action) && "VOICE".equals(sourceType)) {
            return "Tạo từ Voice";
        }
        return switch (action) {
            case "CREATE" -> "Tạo giao dịch";
            case "UPDATE" -> "Cập nhật giao dịch";
            case "SOFT_DELETE", "DELETE" -> "Xóa giao dịch";
            case "RESTORE" -> "Khôi phục giao dịch";
            case "IMPORT" -> "Nhập giao dịch";
            default -> "Thay đổi giao dịch";
        };
    }

    private String sourceLabel(String value) {
        if (value == null) {
            return "Không rõ";
        }
        return switch (value) {
            case "VOICE" -> "Voice";
            case "MANUAL" -> "Nhập thủ công";
            case "EXCEL_MIGRATION" -> "Excel";
            case "SYSTEM" -> "Hệ thống";
            case "QUICK_BUTTON" -> "Nút nhanh";
            case "QUICK_TEXT" -> "Nhập nhanh";
            default -> value;
        };
    }

    private String typeLabel(String value) {
        if (value == null) {
            return "Không rõ";
        }
        return switch (value) {
            case "EXPENSE" -> "Chi tiêu";
            case "INCOME" -> "Thu nhập";
            case "TRANSFER" -> "Chuyển ví";
            case "ADJUSTMENT" -> "Điều chỉnh";
            default -> value;
        };
    }

    private String spendingScopeLabel(String value) {
        return switch (value) {
            case "PERSONAL" -> "Cá nhân";
            case "FAMILY" -> "Gia đình";
            case "SHARED" -> "Chung";
            case "WORK" -> "Công việc";
            case "OTHER" -> "Khác";
            default -> value;
        };
    }

    private String adjustmentDirectionLabel(String value) {
        return switch (value) {
            case "INCREASE" -> "Tăng";
            case "DECREASE" -> "Giảm";
            default -> value;
        };
    }

    private String fieldLabel(String field) {
        return switch (field) {
            case "amount" -> "Số tiền";
            case "type" -> "Loại giao dịch";
            case "status" -> "Trạng thái";
            case "transactionDate" -> "Ngày giao dịch";
            case "transactionTime" -> "Giờ giao dịch";
            case "description" -> "Mô tả";
            case "note" -> "Ghi chú";
            case "walletId" -> "Ví";
            case "categoryId" -> "Danh mục";
            case "incomeSourceId" -> "Nguồn thu";
            case "relatedIncomeSourceId" -> "Nguồn thu liên quan";
            case "sourceWalletId" -> "Ví nguồn";
            case "destinationWalletId" -> "Ví nhận";
            case "attributedPersonId" -> "Người liên quan";
            case "spendingScope" -> "Phạm vi chi tiêu";
            case "adjustmentDirection" -> "Hướng điều chỉnh";
            case "voiceRecordId" -> "Bản ghi Voice";
            case "sourceType" -> "Nguồn tạo";
            case "rawInput" -> "Nội dung nguồn";
            case "deletedAt" -> "Trạng thái xóa";
            case "createdAt" -> "Thời điểm tạo";
            case "updatedAt" -> "Thời điểm cập nhật";
            default -> field;
        };
    }

    private boolean displayOnlyField(String field) {
        return Set.of("walletName", "categoryName", "attributedPersonName", "sourceWalletName", "destinationWalletName").contains(field);
    }

    private boolean technicalField(String field) {
        return field.endsWith("Id")
                || Set.of("voiceRecordId", "sourceReference", "migrationKey", "walletUnknown", "historical", "affectsWalletBalance").contains(field);
    }

    private boolean importantField(String field) {
        return Set.of("amount", "type", "status", "transactionDate", "transactionTime", "description", "note",
                "walletId", "categoryId", "incomeSourceId", "relatedIncomeSourceId", "sourceWalletId",
                "destinationWalletId", "attributedPersonId", "spendingScope", "adjustmentDirection",
                "sourceType", "deletedAt").contains(field);
    }

    private String valueType(String field) {
        if (field.endsWith("Id")) {
            return "ENTITY_ID";
        }
        if ("amount".equals(field)) {
            return "MONEY";
        }
        if (Set.of("type", "status", "sourceType", "spendingScope", "adjustmentDirection").contains(field)) {
            return "ENUM";
        }
        if (field.endsWith("At")) {
            return "DATETIME";
        }
        if (field.endsWith("Date")) {
            return "DATE";
        }
        return "TEXT";
    }
}
