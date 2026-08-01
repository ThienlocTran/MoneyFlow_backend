package com.moneyflowbackend.voice.command;

import com.moneyflowbackend.voice.dto.VoiceQueryResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCommandInterpretResponse {
    private VoiceCommandMode mode;
    private String status;
    private String commandType;
    private String text;
    private UUID voiceRecordId;
    private VoiceReviewDraftResponse review;
    private VoiceQueryResponse query;
    private String answerText;
    @Builder.Default
    private List<VoiceReviewDraftResponse.DraftItem> drafts = new ArrayList<>();
    @Builder.Default
    private List<VoiceCommandWarningDto> warnings = new ArrayList<>();
}
