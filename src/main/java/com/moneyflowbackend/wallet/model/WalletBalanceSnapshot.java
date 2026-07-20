package com.moneyflowbackend.wallet.model;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.closing.model.DailyClosing;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "wallet_balance_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletBalanceSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_closing_id")
    private DailyClosing dailyClosing;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "ledger_balance", precision = 19, scale = 2)
    private BigDecimal ledgerBalance;

    @Column(precision = 19, scale = 2)
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    @Builder.Default
    private BalanceSourceType sourceType = BalanceSourceType.MANUAL;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "migration_key", length = 64)
    private String migrationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", length = 20)
    private ReconciliationStatus reconciliationStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_transaction_id")
    private Transaction adjustmentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;
}
