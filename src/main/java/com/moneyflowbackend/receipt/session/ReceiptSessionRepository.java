package com.moneyflowbackend.receipt.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptSessionRepository extends JpaRepository<ReceiptSession, UUID> {
    Optional<ReceiptSession> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);
}
