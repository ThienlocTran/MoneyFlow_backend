package com.moneyflowbackend.voice.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewConfirmRequest;
import com.moneyflowbackend.voice.dto.VoiceReviewConfirmResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftRequest;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewParseRequest;
import com.moneyflowbackend.voice.service.VoiceReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/voice-review")
public class VoiceReviewController {
    private final VoiceReviewService voiceReviewService;

    public VoiceReviewController(VoiceReviewService voiceReviewService) {
        this.voiceReviewService = voiceReviewService;
    }

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<VoiceReviewDraftResponse>> parse(
            @PathVariable UUID workspaceId,
            @RequestBody VoiceReviewParseRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Voice review draft parsed", voiceReviewService.parse(workspaceId, req, currentUserId())));
    }

    @PatchMapping("/{voiceRecordId}/draft")
    public ResponseEntity<ApiResponse<VoiceReviewDraftResponse>> patchDraft(
            @PathVariable UUID workspaceId,
            @PathVariable UUID voiceRecordId,
            @RequestBody VoiceReviewDraftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Voice review draft validated", voiceReviewService.patchDraft(workspaceId, voiceRecordId, req, currentUserId())));
    }

    @PostMapping("/{voiceRecordId}/confirm")
    public ResponseEntity<ApiResponse<VoiceReviewConfirmResponse>> confirm(
            @PathVariable UUID workspaceId,
            @PathVariable UUID voiceRecordId,
            @RequestBody VoiceReviewConfirmRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Voice review transaction posted", voiceReviewService.confirm(workspaceId, voiceRecordId, req, currentUserId())));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
