package com.moneyflowbackend.voice.command;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.voice.dto.VoiceQueryRequest;
import com.moneyflowbackend.voice.dto.VoiceQueryResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftResponse;
import com.moneyflowbackend.voice.dto.VoiceReviewDraftType;
import com.moneyflowbackend.voice.dto.VoiceReviewParseRequest;
import com.moneyflowbackend.voice.service.VoiceQueryService;
import com.moneyflowbackend.voice.service.VoiceReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VoiceCommandService {
    private static final String MIXED_MESSAGE = "Câu này vừa có ghi giao dịch vừa có câu hỏi. Hãy tách thành 2 lần để tránh lưu sai.";
    private static final String UNSUPPORTED_MESSAGE = "MoneyFlow chưa hỗ trợ câu này.";
    private static final String CLARIFY_MESSAGE = "MoneyFlow cần bạn nói rõ hơn.";
    private static final String ROUTED_TO_REVIEW_MESSAGE = "MoneyFlow đã tạo bản nháp để bạn kiểm tra trước khi lưu.";
    private static final String ROUTED_TO_QUERY_MESSAGE = "MoneyFlow chỉ xem số liệu, không tạo giao dịch.";

    private final VoiceCommandClassifier classifier;
    private final VoiceReviewService voiceReviewService;
    private final VoiceQueryService voiceQueryService;

    public VoiceCommandService(
            VoiceCommandClassifier classifier,
            VoiceReviewService voiceReviewService,
            VoiceQueryService voiceQueryService) {
        this.classifier = classifier;
        this.voiceReviewService = voiceReviewService;
        this.voiceQueryService = voiceQueryService;
    }

    public VoiceCommandInterpretResponse interpret(UUID workspaceId, VoiceCommandInterpretRequest req, UUID userId) {
        String text = text(req);
        return switch (classifier.route(text)) {
            case QUERY -> query(workspaceId, req, userId, text);
            case REVIEW -> review(workspaceId, req, userId, text);
            case MIXED -> simple(VoiceCommandMode.NEEDS_CLARIFICATION, "NEEDS_CLARIFICATION", "NEEDS_CLARIFICATION", text,
                    MIXED_MESSAGE, "VOICE_COMMAND_MIXED_QUERY_AND_DRAFT");
            case NEEDS_CLARIFICATION -> simple(VoiceCommandMode.NEEDS_CLARIFICATION, "NEEDS_CLARIFICATION", "NEEDS_CLARIFICATION", text,
                    CLARIFY_MESSAGE, "VOICE_COMMAND_NEEDS_CLARIFICATION");
        };
    }

    public VoiceCommandInterpretResponse interpretSession(UUID workspaceId, VoiceCommandInterpretRequest req, UUID userId) {
        String text = text(req);
        return switch (classifier.route(text)) {
            case QUERY -> query(workspaceId, req, userId, text);
            case REVIEW -> reviewPreview(workspaceId, userId, text);
            case MIXED -> simple(VoiceCommandMode.NEEDS_CLARIFICATION, "NEEDS_CLARIFICATION", "NEEDS_CLARIFICATION", text,
                    MIXED_MESSAGE, "VOICE_COMMAND_MIXED_QUERY_AND_DRAFT");
            case NEEDS_CLARIFICATION -> simple(VoiceCommandMode.NEEDS_CLARIFICATION, "NEEDS_CLARIFICATION", "NEEDS_CLARIFICATION", text,
                    CLARIFY_MESSAGE, "VOICE_COMMAND_NEEDS_CLARIFICATION");
        };
    }

    private VoiceCommandInterpretResponse query(UUID workspaceId, VoiceCommandInterpretRequest req, UUID userId, String text) {
        VoiceQueryRequest queryReq = VoiceQueryRequest.builder()
                .text(text)
                .timezone(req == null ? null : req.getTimezone())
                .now(req == null ? null : req.getNow())
                .build();
        VoiceQueryResponse query = voiceQueryService.ask(workspaceId, queryReq, userId);
        return VoiceCommandInterpretResponse.builder()
                .mode(VoiceCommandMode.READ_ONLY_QUERY)
                .status(query.getStatus())
                .commandType("READ_ONLY_QUERY")
                .text(text)
                .query(query)
                .answerText(query.getAnswerText())
                .warnings(List.of(warning("VOICE_COMMAND_ROUTED_TO_QUERY", ROUTED_TO_QUERY_MESSAGE)))
                .build();
    }

    private VoiceCommandInterpretResponse review(UUID workspaceId, VoiceCommandInterpretRequest req, UUID userId, String text) {
        VoiceReviewParseRequest parseReq = new VoiceReviewParseRequest();
        parseReq.setText(text);
        parseReq.setTranscript(text);
        parseReq.setRawInput(text);
        VoiceReviewDraftResponse review = voiceReviewService.parse(workspaceId, parseReq, userId);
        return reviewResponse(text, review);
    }

    private VoiceCommandInterpretResponse reviewPreview(UUID workspaceId, UUID userId, String text) {
        VoiceReviewDraftResponse review = voiceReviewService.preview(workspaceId, text, userId);
        return reviewResponse(text, review);
    }

    private VoiceCommandInterpretResponse reviewResponse(String text, VoiceReviewDraftResponse review) {
        VoiceCommandMode mode = mode(review);
        boolean unsupported = mode == VoiceCommandMode.UNSUPPORTED;
        return VoiceCommandInterpretResponse.builder()
                .mode(mode)
                .status(unsupported ? "UNSUPPORTED" : "NEEDS_REVIEW")
                .commandType(unsupported ? "UNSUPPORTED" : "LEDGER_DRAFT")
                .text(text)
                .voiceRecordId(review.getVoiceRecordId())
                .review(review)
                .drafts(review.getDrafts())
                .answerText(unsupported ? UNSUPPORTED_MESSAGE : ROUTED_TO_REVIEW_MESSAGE)
                .warnings(reviewWarnings(review, unsupported))
                .build();
    }

    private List<VoiceCommandWarningDto> reviewWarnings(VoiceReviewDraftResponse review, boolean unsupported) {
        List<VoiceCommandWarningDto> warnings = new ArrayList<>();
        warnings.add(warning(unsupported ? "VOICE_COMMAND_UNSUPPORTED" : "VOICE_COMMAND_ROUTED_TO_REVIEW",
                unsupported ? UNSUPPORTED_MESSAGE : ROUTED_TO_REVIEW_MESSAGE));
        if (review.getWarningDetails() != null) {
            review.getWarningDetails().stream()
                    .filter(item -> "VOICE_MULTI_INTENT_DETECTED".equals(item.getCode()))
                    .findFirst()
                    .ifPresent(item -> warnings.add(warning(item.getCode(), item.getMessage())));
        }
        return warnings;
    }

    private VoiceCommandMode mode(VoiceReviewDraftResponse review) {
        if (review.getDrafts() != null && review.getDrafts().size() > 1) {
            return VoiceCommandMode.MULTI_DRAFT_REVIEW;
        }
        VoiceReviewDraftType type = review.getCandidate() == null ? null : review.getCandidate().getType();
        if (type == VoiceReviewDraftType.INCOME_FACT) {
            return VoiceCommandMode.INCOME_FACT_REVIEW;
        }
        if (type == VoiceReviewDraftType.WALLET_SNAPSHOT) {
            return VoiceCommandMode.WALLET_SNAPSHOT_REVIEW;
        }
        if (type == VoiceReviewDraftType.DEBT
                || type == VoiceReviewDraftType.LOAN_DISBURSEMENT
                || type == VoiceReviewDraftType.LOAN_COLLECTION
                || type == VoiceReviewDraftType.BORROWING_RECEIPT
                || type == VoiceReviewDraftType.BORROWING_REPAYMENT) {
            return VoiceCommandMode.DEBT_DRAFT;
        }
        if (type == VoiceReviewDraftType.UNKNOWN || type == null) {
            return VoiceCommandMode.UNSUPPORTED;
        }
        return VoiceCommandMode.TRANSACTION_REVIEW;
    }

    private VoiceCommandInterpretResponse simple(VoiceCommandMode mode, String status, String commandType, String text, String message, String code) {
        return VoiceCommandInterpretResponse.builder()
                .mode(mode)
                .status(status)
                .commandType(commandType)
                .text(text)
                .answerText(message)
                .warnings(List.of(warning(code, message)))
                .build();
    }

    private VoiceCommandWarningDto warning(String code, String message) {
        return VoiceCommandWarningDto.builder()
                .code(code)
                .message(message)
                .build();
    }

    private String text(VoiceCommandInterpretRequest req) {
        String text = req == null ? null : req.getText();
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException("VOICE_COMMAND_TEXT_REQUIRED", "Voice command text is required", HttpStatus.BAD_REQUEST);
        }
        if (text.length() > 500) {
            throw new BusinessException("VOICE_COMMAND_TEXT_TOO_LONG", "Voice command text is too long", HttpStatus.BAD_REQUEST);
        }
        return text.trim().replaceAll("\\s+", " ");
    }
}
