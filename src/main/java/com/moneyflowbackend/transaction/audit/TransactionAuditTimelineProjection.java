package com.moneyflowbackend.transaction.audit;

import java.time.Instant;
import java.util.UUID;

public interface TransactionAuditTimelineProjection {
    UUID getId();
    Instant getOccurredAt();
    TransactionAuditAction getAuditAction();
    UUID getTransactionId();
    UUID getActorUserId();
    String getActorDisplayName();
}
