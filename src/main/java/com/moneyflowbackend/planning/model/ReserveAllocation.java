package com.moneyflowbackend.planning.model;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.workspace.model.Workspace;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reserve_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, columnDefinition = "CHAR(3)")
    @JdbcTypeCode(Types.CHAR)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReserveAllocationStatus status = ReserveAllocationStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose_type", nullable = false, length = 30)
    @Builder.Default
    private ReservePurposeType purposeType = ReservePurposeType.CUSTOM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jar_id")
    private Jar jar;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
