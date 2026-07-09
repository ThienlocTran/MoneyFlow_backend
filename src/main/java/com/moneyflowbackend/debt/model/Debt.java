package com.moneyflowbackend.debt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "debts")
@Getter
@Setter
public class Debt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "counterparty_person_id", nullable = false)
    private UUID counterpartyPersonId;

    @Column(nullable = false, length = 20)
    private String direction;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "debt_status", nullable = false, length = 20)
    private String debtStatus = "OPEN";

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "origin_transaction_id")
    private UUID originTransactionId;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "migration_key")
    private String migrationKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
