package com.moneyflowbackend.voice.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoiceSessionDraftRepository extends JpaRepository<VoiceSessionDraft, UUID> {
    List<VoiceSessionDraft> findAllByVoiceSessionIdOrderByDraftIndexAsc(UUID voiceSessionId);

    boolean existsByVoiceSessionIdAndStatus(UUID voiceSessionId, VoiceSessionDraftStatus status);

    void deleteByVoiceSessionId(UUID voiceSessionId);
}
