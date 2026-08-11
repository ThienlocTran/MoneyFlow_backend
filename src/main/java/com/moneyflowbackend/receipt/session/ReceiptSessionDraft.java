package com.moneyflowbackend.receipt.session;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "receipt_session_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptSessionDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_session_id", nullable = false)
    private ReceiptSession receiptSession;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "draft_index", nullable = false)
    private int draftIndex;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String type = "EXPENSE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReceiptSessionDraftStatus status = ReceiptSessionDraftStatus.NEEDS_REVIEW;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_hint")
    private String categoryHint;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column
    private Double confidence;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    @Column(name = "confirmed_entity_type", length = 30)
    private String confirmedEntityType;

    @Column(name = "confirmed_entity_id")
    private UUID confirmedEntityId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
