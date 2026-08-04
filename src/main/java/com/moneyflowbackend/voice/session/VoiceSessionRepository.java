package com.moneyflowbackend.voice.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoiceSessionRepository extends JpaRepository<VoiceSession, UUID> {
    Optional<VoiceSession> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
