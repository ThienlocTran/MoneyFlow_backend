package com.moneyflowbackend.voice.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.voice.dto.VoiceQueryRequest;
import com.moneyflowbackend.voice.dto.VoiceQueryResponse;
import com.moneyflowbackend.voice.service.VoiceQueryService;
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
@RequestMapping("/api/workspaces/{workspaceId}/voice-query")
public class VoiceQueryController {

    private final VoiceQueryService voiceQueryService;

    public VoiceQueryController(VoiceQueryService voiceQueryService) {
        this.voiceQueryService = voiceQueryService;
    }

    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<VoiceQueryResponse>> ask(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid VoiceQueryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Voice query processed", voiceQueryService.ask(workspaceId, req, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
