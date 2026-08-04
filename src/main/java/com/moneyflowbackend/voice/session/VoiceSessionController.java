package com.moneyflowbackend.voice.session;

import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/voice-sessions")
public class VoiceSessionController {
    private final VoiceSessionService voiceSessionService;

    public VoiceSessionController(VoiceSessionService voiceSessionService) {
        this.voiceSessionService = voiceSessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VoiceSessionCreateResponse>> create(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) VoiceSessionCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Voice session created", voiceSessionService.create(workspaceId, req, currentUserId())));
    }

    @PatchMapping("/{sessionId}/transcript")
    public ResponseEntity<ApiResponse<VoiceSessionDetailResponse>> updateTranscript(
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId,
            @RequestBody VoiceSessionTranscriptUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Voice session transcript updated", voiceSessionService.updateTranscript(workspaceId, sessionId, req, currentUserId())));
    }

    @PostMapping("/{sessionId}/interpret")
    public ResponseEntity<ApiResponse<VoiceSessionDetailResponse>> interpret(
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId,
            @RequestBody(required = false) VoiceSessionInterpretRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Voice session interpreted", voiceSessionService.interpret(workspaceId, sessionId, req, currentUserId())));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<VoiceSessionDetailResponse>> get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok("Voice session loaded", voiceSessionService.get(workspaceId, sessionId, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
