package com.moneyflowbackend.obligation.model;

import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "obligation_occurrences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_obligation_occurrences_template_period", columnNames = {"template_id", "period_key"}),
                @UniqueConstraint(name = "uq_obligation_occurrences_linked_transaction", columnNames = "linked_transaction_id")
        },
        check = {
                @CheckConstraint(name = "chk_obligation_occurrences_expected_amount", constraint = "expected_amount IS NULL OR expected_amount > 0"),
                @CheckConstraint(name = "chk_obligation_occurrences_actual_amount", constraint = "actual_amount IS NULL OR actual_amount > 0"),
                @CheckConstraint(name = "chk_obligation_occurrences_state_integrity", constraint = """
                        (status = 'CONFIRMED' AND linked_transaction_id IS NOT NULL AND actual_amount IS NOT NULL AND completed_at IS NOT NULL)
                        OR (status = 'PENDING' AND linked_transaction_id IS NULL AND completed_at IS NULL AND skipped_at IS NULL)
                        OR (status = 'SKIPPED' AND linked_transaction_id IS NULL AND skipped_at IS NOT NULL)
                        OR (status = 'CANCELLED' AND linked_transaction_id IS NULL)
                        """)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObligationOccurrence {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RecurringObligationTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "period_key", nullable = false, length = 32)
    private String periodKey;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "reminder_date")
    private LocalDate reminderDate;

    @Column(name = "expected_amount", precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ObligationOccurrenceStatus status = ObligationOccurrenceStatus.PENDING;

    @Column(name = "snoozed_until")
    private LocalDate snoozedUntil;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_transaction_id")
    private Transaction linkedTransaction;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Column(name = "skip_reason", length = 500)
    private String skipReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
