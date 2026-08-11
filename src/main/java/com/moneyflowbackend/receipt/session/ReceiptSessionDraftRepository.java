package com.moneyflowbackend.receipt.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReceiptSessionDraftRepository extends JpaRepository<ReceiptSessionDraft, UUID> {
    List<ReceiptSessionDraft> findAllByReceiptSessionIdOrderByDraftIndexAsc(UUID receiptSessionId);

    void deleteByReceiptSessionId(UUID receiptSessionId);
}
