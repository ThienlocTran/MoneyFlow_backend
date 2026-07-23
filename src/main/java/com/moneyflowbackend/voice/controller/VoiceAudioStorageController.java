package com.moneyflowbackend.voice.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.voice.dto.VoiceAudioStorageStatusResponse;
import com.moneyflowbackend.voice.service.VoiceAudioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/voice/audio-storage")
public class VoiceAudioStorageController {
    private final VoiceAudioService voiceAudioService;

    public VoiceAudioStorageController(VoiceAudioService voiceAudioService) {
        this.voiceAudioService = voiceAudioService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<VoiceAudioStorageStatusResponse>> status(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Voice audio storage status loaded",
                voiceAudioService.storageStatus(workspaceId, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
