package com.moneyflowbackend.voice.session;

import com.moneyflowbackend.voice.command.VoiceCommandWarningDto;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftResponse;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class VoiceSessionDraftMapper {
    VoiceSessionDraft toEntity(VoiceSession session, VoiceReviewDraftResponse.DraftItem item) {
        VoiceReviewDraftResponse.Candidate candidate = item.getCandidate();
        boolean confirmable = item.isCanConfirm() || (candidate != null && candidate.isCanConfirm());
        VoiceSessionDraftStatus status = status(candidate, confirmable);
        return VoiceSessionDraft.builder()
                .voiceSession(session)
                .draftIndex(item.getIndex())
                .sourceText(item.getSourceText())
                .normalizedSourceText(normalize(item.getSourceText()))
                .type(candidate == null || candidate.getType() == null ? null : candidate.getType().name())
                .amount(candidate == null ? null : candidate.getAmount())
                .currency(candidate == null || candidate.getCurrency() == null ? "VND" : candidate.getCurrency())
                .walletId(candidate == null ? null : candidate.getWalletId())
                .categoryId(candidate == null ? null : candidate.getCategoryId())
                .jarId(candidate == null ? null : candidate.getJarId())
                .fundId(candidate == null ? null : candidate.getTargetFundId())
                .debtId(candidate == null ? null : candidate.getDebtId())
                .counterpartyId(candidate == null ? null : candidate.getCounterpartyId())
                .transactionType(candidate == null || candidate.getType() == null ? null : candidate.getType().name())
                .movementType(candidate == null ? null : candidate.getDebtDirection())
                .affectsWalletBalance(candidate == null ? null : candidate.isAffectsWalletBalance())
                .walletRequired(candidate != null && candidate.isWalletRequired())
                .categoryRequired(candidate != null && candidate.isCategoryRequired())
                .confirmable(confirmable)
                .status(status)
                .warningsJson(writeWarnings(item.getWarnings()))
                .build();
    }

    VoiceSessionDraftResponse toResponse(VoiceSessionDraft draft) {
        return VoiceSessionDraftResponse.builder()
                .draftId(draft.getId())
                .draftIndex(draft.getDraftIndex())
                .sourceText(draft.getSourceText())
                .normalizedSourceText(draft.getNormalizedSourceText())
                .type(draft.getType())
                .amount(draft.getAmount())
                .currency(draft.getCurrency())
                .walletId(draft.getWalletId())
                .categoryId(draft.getCategoryId())
                .jarId(draft.getJarId())
                .fundId(draft.getFundId())
                .debtId(draft.getDebtId())
                .counterpartyId(draft.getCounterpartyId())
                .transactionType(draft.getTransactionType())
                .movementType(draft.getMovementType())
                .affectsWalletBalance(draft.getAffectsWalletBalance())
                .walletRequired(draft.isWalletRequired())
                .categoryRequired(draft.isCategoryRequired())
                .confirmable(draft.isConfirmable())
                .status(draft.getStatus())
                .warnings(readWarnings(draft.getWarningsJson()))
                .confirmedEntityType(draft.getConfirmedEntityType())
                .confirmedEntityId(draft.getConfirmedEntityId())
                .confirmedAt(draft.getConfirmedAt())
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    List<VoiceSessionWarningResponse> commandWarnings(List<VoiceCommandWarningDto> warnings) {
        if (warnings == null) return List.of();
        return warnings.stream()
                .map(warning -> VoiceSessionWarningResponse.builder()
                        .code(warning.getCode())
                        .message(warning.getMessage())
                        .build())
                .toList();
    }

    String writeWarnings(List<VoiceReviewDraftResponse.Warning> warnings) {
        if (warnings == null || warnings.isEmpty()) return null;
        return warnings.stream()
                .map(VoiceReviewDraftResponse.Warning::getCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(","));
    }

    String writeCommandWarnings(List<VoiceCommandWarningDto> warnings) {
        if (warnings == null || warnings.isEmpty()) return null;
        return warnings.stream()
                .map(VoiceCommandWarningDto::getCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(","));
    }

    List<VoiceSessionWarningResponse> readWarnings(String warningsJson) {
        if (warningsJson == null || warningsJson.isBlank()) return List.of();
        return Arrays.stream(warningsJson.split(","))
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .map(code -> VoiceSessionWarningResponse.builder().code(code).build())
                .toList();
    }

    private VoiceSessionDraftStatus status(VoiceReviewDraftResponse.Candidate candidate, boolean confirmable) {
        if (confirmable) return VoiceSessionDraftStatus.READY;
        if (candidate == null || candidate.getType() == null || "UNKNOWN".equals(candidate.getType().name())) {
            return VoiceSessionDraftStatus.UNSUPPORTED;
        }
        return VoiceSessionDraftStatus.NEEDS_REVIEW;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    }
}
