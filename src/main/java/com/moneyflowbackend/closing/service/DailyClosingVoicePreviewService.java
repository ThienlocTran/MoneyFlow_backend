package com.moneyflowbackend.closing.service;

import com.moneyflowbackend.closing.dto.DailyClosingVoicePreviewRequest;
import com.moneyflowbackend.closing.dto.DailyClosingVoicePreviewResponse;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DailyClosingVoicePreviewService {
    private final WorkspaceService workspaceService;
    private final WalletRepository walletRepository;
    private final DailyClosingVoiceParser parser;
    private final Clock clock;

    public DailyClosingVoicePreviewService(
            WorkspaceService workspaceService,
            WalletRepository walletRepository,
            DailyClosingVoiceParser parser,
            Clock clock) {
        this.workspaceService = workspaceService;
        this.walletRepository = walletRepository;
        this.parser = parser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DailyClosingVoicePreviewResponse preview(UUID workspaceId, DailyClosingVoicePreviewRequest req, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        String transcript = req == null ? null : req.getTranscript();
        if (transcript == null || transcript.trim().isBlank()) {
            throw new BusinessException("TRANSCRIPT_REQUIRED", "Vui lòng nhập nội dung chốt sổ.", HttpStatus.BAD_REQUEST);
        }
        LocalDate closingDate = req.getClosingDate() == null ? LocalDate.now(clock) : req.getClosingDate();
        List<Wallet> wallets = walletRepository.findActiveOpenOnDate(workspaceId, closingDate);
        List<DailyClosingVoicePreviewResponse.Warning> warnings = new ArrayList<>();
        if (wallets.isEmpty()) {
            warnings.add(warning("NO_WALLETS", "Không có ví nào để đối chiếu.", "WARN"));
        }
        DailyClosingVoiceParser.ParsedPreview parsed = wallets.isEmpty()
                ? new DailyClosingVoiceParser.ParsedPreview(List.of(), List.of(), List.of())
                : parser.parse(transcript, wallets);
        parsed.unmatchedSegments().forEach(segment -> warnings.add(warning("UNMATCHED_SEGMENT", segment, "WARN")));

        return DailyClosingVoicePreviewResponse.builder()
                .workspaceId(workspaceId)
                .closingDate(closingDate)
                .transcript(transcript)
                .candidateStatus(status(parsed, warnings))
                .walletBalanceCandidates(parsed.candidates())
                .skippedWallets(parsed.skippedWallets())
                .unmatchedSegments(parsed.unmatchedSegments())
                .warnings(warnings)
                .build();
    }

    private String status(DailyClosingVoiceParser.ParsedPreview parsed, List<DailyClosingVoicePreviewResponse.Warning> warnings) {
        if (parsed.candidates().isEmpty() && parsed.skippedWallets().isEmpty()) {
            return "EMPTY";
        }
        boolean allReady = parsed.candidates().stream().allMatch(candidate -> "READY".equals(candidate.getStatus()));
        return allReady && warnings.isEmpty() ? "READY" : "NEEDS_REVIEW";
    }

    private DailyClosingVoicePreviewResponse.Warning warning(String code, String message, String severity) {
        return DailyClosingVoicePreviewResponse.Warning.builder()
                .code(code)
                .message(message)
                .severity(severity)
                .build();
    }
}
