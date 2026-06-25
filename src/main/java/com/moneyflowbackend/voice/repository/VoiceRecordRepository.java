package com.moneyflowbackend.voice.repository;

import com.moneyflowbackend.voice.model.VoiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoiceRecordRepository extends JpaRepository<VoiceRecord, UUID> {
    Optional<VoiceRecord> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    List<VoiceRecord> findAllByRetentionUntilBeforeAndStoragePublicIdIsNotNull(LocalDate date);
}
