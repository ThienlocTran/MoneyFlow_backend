package com.moneyflowbackend.workspace.model;

import com.moneyflowbackend.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workspace {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_type", nullable = false, length = 20)
    @Builder.Default
    private WorkspaceType workspaceType = WorkspaceType.PERSONAL;

    @Column(nullable = false, columnDefinition = "CHAR(3)")
    @JdbcTypeCode(Types.CHAR)
    @Builder.Default
    private String currency = "VND";

    @Column(nullable = false, length = 80)
    @Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "quick_amount_unit", nullable = false, length = 20)
    @Builder.Default
    private String quickAmountUnit = "THOUSAND";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;
}