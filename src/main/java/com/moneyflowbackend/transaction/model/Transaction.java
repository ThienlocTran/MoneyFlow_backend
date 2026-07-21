package com.moneyflowbackend.transaction.model;

import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.workspace.model.WorkspacePerson;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.income.model.IncomeSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        check = {
                @CheckConstraint(name = "chk_transactions_income_source_links", constraint = """
                        income_source_id IS NULL
                        OR (transaction_type = 'INCOME' AND related_income_source_id IS NULL)
                        """),
                @CheckConstraint(name = "chk_transactions_related_income_source_links", constraint = """
                        related_income_source_id IS NULL
                        OR (transaction_type = 'EXPENSE' AND income_source_id IS NULL)
                        """),
                @CheckConstraint(name = "chk_transactions_single_income_source_link", constraint = """
                        income_source_id IS NULL OR related_income_source_id IS NULL
                        """)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attributed_person_id")
    private WorkspacePerson attributedPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "income_source_id")
    private IncomeSource incomeSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_income_source_id")
    private IncomeSource relatedIncomeSource;

    @Column(name = "voice_record_id")
    private UUID voiceRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_direction", length = 20)
    private AdjustmentDirection adjustmentDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "spending_scope", length = 20)
    private SpendingScope spendingScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus transactionStatus = TransactionStatus.POSTED;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, columnDefinition = "CHAR(3)")
    @JdbcTypeCode(Types.CHAR)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "transaction_time")
    private LocalTime transactionTime;

    @Column(length = 500)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    @Builder.Default
    private TransactionSourceType sourceType = TransactionSourceType.MANUAL;

    @Column(name = "raw_input", columnDefinition = "TEXT")
    private String rawInput;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "migration_key", length = 64)
    private String migrationKey;

    @Column(name = "wallet_unknown", nullable = false)
    @Builder.Default
    private boolean walletUnknown = false;

    @Column(name = "is_historical", nullable = false)
    @Builder.Default
    private boolean historical = false;

    @Column(name = "affects_wallet_balance", nullable = false)
    @Builder.Default
    private boolean affectsWalletBalance = true;

    @Column(name = "legacy_label", length = 255)
    private String legacyLabel;

    @Column(name = "legacy_aggregate", nullable = false)
    @Builder.Default
    private boolean legacyAggregate = false;

    @Column(name = "legacy_ambiguous", nullable = false)
    @Builder.Default
    private boolean legacyAmbiguous = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
