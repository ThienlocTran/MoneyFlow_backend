package com.moneyflowbackend.voice.command;

import com.moneyflowbackend.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/voice-command")
public class VoiceCommandController {
    private final VoiceCommandService voiceCommandService;

    public VoiceCommandController(VoiceCommandService voiceCommandService) {
        this.voiceCommandService = voiceCommandService;
    }

    @PostMapping("/interpret")
    public ResponseEntity<ApiResponse<VoiceCommandInterpretResponse>> interpret(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid VoiceCommandInterpretRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Voice command interpreted", voiceCommandService.interpret(workspaceId, req, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
