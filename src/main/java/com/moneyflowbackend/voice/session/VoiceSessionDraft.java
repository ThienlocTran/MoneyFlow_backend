package com.moneyflowbackend.voice.session;

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
import java.util.UUID;

@Entity
@Table(name = "voice_session_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSessionDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_session_id", nullable = false)
    private VoiceSession voiceSession;

    @Column(name = "draft_index", nullable = false)
    private int draftIndex;

    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "normalized_source_text", columnDefinition = "TEXT")
    private String normalizedSourceText;

    @Column(length = 60)
    private String type;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "jar_id")
    private UUID jarId;

    @Column(name = "fund_id")
    private UUID fundId;

    @Column(name = "debt_id")
    private UUID debtId;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Column(name = "transaction_type", length = 60)
    private String transactionType;

    @Column(name = "movement_type", length = 60)
    private String movementType;

    @Column(name = "affects_wallet_balance")
    private Boolean affectsWalletBalance;

    @Column(name = "wallet_required", nullable = false)
    @Builder.Default
    private boolean walletRequired = false;

    @Column(name = "category_required", nullable = false)
    @Builder.Default
    private boolean categoryRequired = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean confirmable = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VoiceSessionDraftStatus status = VoiceSessionDraftStatus.DRAFT;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    @Column(name = "confirmed_entity_type", length = 60)
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
