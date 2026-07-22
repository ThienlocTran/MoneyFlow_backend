package com.moneyflowbackend.emergencyfund.model;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "emergency_fund_plans",
        check = {
                @CheckConstraint(name = "chk_emergency_fund_target_months", constraint = "target_months > 0"),
                @CheckConstraint(name = "chk_emergency_fund_basis_mode", constraint = "basis_mode IN ('MANUAL')"),
                @CheckConstraint(name = "chk_emergency_fund_manual_expense", constraint = "manual_monthly_expense > 0"),
                @CheckConstraint(name = "chk_emergency_fund_plan_status", constraint = "plan_status IN ('ACTIVE', 'PAUSED')")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyFundPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "target_months", nullable = false)
    private Integer targetMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis_mode", nullable = false, length = 20)
    @Builder.Default
    private EmergencyFundBasisMode basisMode = EmergencyFundBasisMode.MANUAL;

    @Column(name = "manual_monthly_expense", nullable = false, precision = 19, scale = 2)
    private BigDecimal manualMonthlyExpense;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status", nullable = false, length = 20)
    @Builder.Default
    private EmergencyFundPlanStatus planStatus = EmergencyFundPlanStatus.ACTIVE;

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

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
