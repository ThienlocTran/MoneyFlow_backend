package com.moneyflowbackend.studentloan.model;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "student_loans",
        check = {
                @CheckConstraint(name = "chk_student_loans_name_not_blank", constraint = "trim(name) <> ''"),
                @CheckConstraint(name = "chk_student_loans_lender_not_blank", constraint = "lender IS NULL OR trim(lender) <> ''"),
                @CheckConstraint(name = "chk_student_loans_original_principal", constraint = "original_principal IS NULL OR original_principal >= 0"),
                @CheckConstraint(name = "chk_student_loans_current_principal", constraint = "current_principal >= 0"),
                @CheckConstraint(name = "chk_student_loans_annual_interest_rate", constraint = "annual_interest_rate >= 0"),
                @CheckConstraint(name = "chk_student_loans_minimum_payment", constraint = "minimum_monthly_payment >= 0"),
                @CheckConstraint(name = "chk_student_loans_extra_payment", constraint = "planned_extra_monthly_payment IS NULL OR planned_extra_monthly_payment >= 0"),
                @CheckConstraint(name = "chk_student_loans_target_date", constraint = "target_payoff_date IS NULL OR start_date IS NULL OR target_payoff_date >= start_date"),
                @CheckConstraint(name = "chk_student_loans_status", constraint = "status IN ('ACTIVE', 'PAID_OFF', 'PAUSED', 'ARCHIVED')")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLoan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 160)
    private String lender;

    @Column(name = "original_principal", precision = 19, scale = 2)
    private BigDecimal originalPrincipal;

    @Column(name = "current_principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPrincipal;

    @Column(name = "annual_interest_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal annualInterestRate;

    @Column(name = "minimum_monthly_payment", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumMonthlyPayment;

    @Column(name = "planned_extra_monthly_payment", precision = 19, scale = 2)
    private BigDecimal plannedExtraMonthlyPayment;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_payoff_date")
    private LocalDate targetPayoffDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StudentLoanStatus status = StudentLoanStatus.ACTIVE;

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
    void normalize() {
        if (name != null) {
            name = name.trim();
        }
        if (lender != null) {
            lender = lender.trim();
            if (lender.isEmpty()) {
                lender = null;
            }
        }
        updatedAt = Instant.now();
    }
}
