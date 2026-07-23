package com.moneyflowbackend.voice.repository;

import com.moneyflowbackend.voice.model.VoiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoiceRecordRepository extends JpaRepository<VoiceRecord, UUID> {
    Optional<VoiceRecord> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    @Query("""
            SELECT v FROM VoiceRecord v
            WHERE v.workspace.id = :workspaceId
              AND v.createdByUser.id = :userId
              AND v.idempotencyKey = :idempotencyKey
            """)
    Optional<VoiceRecord> findVoiceIdempotencyMatch(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey);
    List<VoiceRecord> findAllByRetentionUntilBeforeAndStoragePublicIdIsNotNull(LocalDate date);
}
