package com.moneyflowbackend.obligation.model;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "recurring_obligation_templates",
        check = {
                @CheckConstraint(name = "chk_recurring_obligation_templates_name_not_blank", constraint = "trim(name) <> ''"),
                @CheckConstraint(name = "chk_recurring_obligation_templates_interval_count", constraint = "interval_count >= 1"),
                @CheckConstraint(name = "chk_recurring_obligation_templates_reminder_days_before", constraint = "reminder_days_before >= 0"),
                @CheckConstraint(name = "chk_recurring_obligation_templates_end_date", constraint = "end_date IS NULL OR end_date >= start_date"),
                @CheckConstraint(name = "chk_recurring_obligation_templates_default_amount", constraint = "default_amount IS NULL OR default_amount > 0"),
                @CheckConstraint(name = "chk_recurring_obligation_templates_fixed_amount", constraint = "amount_mode <> 'FIXED' OR default_amount IS NOT NULL")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringObligationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObligationDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_mode", nullable = false, length = 20)
    private ObligationAmountMode amountMode;

    @Column(name = "default_amount", precision = 19, scale = 2)
    private BigDecimal defaultAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObligationFrequency frequency;

    @Column(name = "interval_count", nullable = false)
    @Builder.Default
    private Integer intervalCount = 1;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "reminder_days_before", nullable = false)
    @Builder.Default
    private Integer reminderDaysBefore = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_wallet_id")
    private Wallet defaultWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_category_id")
    private Category defaultCategory;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecurringObligationStatus status = RecurringObligationStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

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
