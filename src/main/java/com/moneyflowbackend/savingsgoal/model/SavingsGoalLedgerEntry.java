package com.moneyflowbackend.savingsgoal.model;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "savings_goal_ledger_entries",
        check = {
                @CheckConstraint(name = "chk_savings_goal_ledger_type", constraint = "entry_type IN ('CONTRIBUTION', 'RELEASE')"),
                @CheckConstraint(name = "chk_savings_goal_ledger_amount_delta", constraint = "amount_delta <> 0"),
                @CheckConstraint(name = "chk_savings_goal_ledger_signed_amount", constraint = "(entry_type = 'CONTRIBUTION' AND amount_delta > 0) OR (entry_type = 'RELEASE' AND amount_delta < 0)")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsGoalLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "savings_goal_id", nullable = false)
    private SavingsGoal savingsGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private SavingsGoalLedgerType entryType;

    @Column(name = "amount_delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountDelta;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actorUser;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    @PreUpdate
    void normalize() {
        if (note != null) {
            note = note.trim();
            if (note.isEmpty()) {
                note = null;
            }
        }
    }
}
