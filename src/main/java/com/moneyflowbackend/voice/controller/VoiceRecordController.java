package com.moneyflowbackend.voice.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.voice.dto.VoiceAudioPlaybackResponse;
import com.moneyflowbackend.voice.dto.VoiceAudioUploadResponse;
import com.moneyflowbackend.voice.service.VoiceAudioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/voice-records")
public class VoiceRecordController {
    private final VoiceAudioService voiceAudioService;

    public VoiceRecordController(VoiceAudioService voiceAudioService) {
        this.voiceAudioService = voiceAudioService;
    }

    @PostMapping("/{voiceRecordId}/audio")
    public ResponseEntity<ApiResponse<VoiceAudioUploadResponse>> uploadAudio(
            @PathVariable UUID voiceRecordId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer durationSeconds) {
        VoiceAudioUploadResponse res = voiceAudioService.uploadAudio(voiceRecordId, file, durationSeconds, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Voice audio uploaded", res));
    }

    @GetMapping("/{voiceRecordId}/playback-url")
    public ResponseEntity<ApiResponse<VoiceAudioPlaybackResponse>> playbackUrl(@PathVariable UUID voiceRecordId) {
        VoiceAudioPlaybackResponse res = voiceAudioService.playbackUrl(voiceRecordId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Voice audio playback URL created", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
