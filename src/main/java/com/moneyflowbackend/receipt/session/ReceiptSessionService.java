package com.moneyflowbackend.receipt.session;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.auth.repository.UserRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.receipt.ocr.ReceiptImageInput;
import com.moneyflowbackend.receipt.ocr.ReceiptOcrResult;
import com.moneyflowbackend.receipt.ocr.ReceiptOcrService;
import com.moneyflowbackend.receipt.ocr.ReceiptOcrStatus;
import com.moneyflowbackend.receipt.service.ReceiptTextParser;
import com.moneyflowbackend.receipt.session.storage.ReceiptImageStorageService;
import com.moneyflowbackend.receipt.session.storage.StoredReceiptImage;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.text.Normalizer;
import java.util.regex.Pattern;

@Service
public class ReceiptSessionService {
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM-yyyy");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ReceiptSessionRepository receiptSessionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final ReceiptImageStorageService storageService;
    private final ReceiptOcrService ocrService;
    private final ReceiptTextParser receiptTextParser;
    private final ReceiptSessionDraftRepository receiptSessionDraftRepository;
    private final ReceiptSessionDraftBuilder receiptSessionDraftBuilder;
    private final Clock clock;
    private final long maxImageBytes;
    private static final Pattern MANY_BLANK_LINES = Pattern.compile("\\n{3,}");

    public ReceiptSessionService(
            ReceiptSessionRepository receiptSessionRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            ReceiptImageStorageService storageService,
            ReceiptOcrService ocrService,
            ReceiptTextParser receiptTextParser,
            ReceiptSessionDraftRepository receiptSessionDraftRepository,
            ReceiptSessionDraftBuilder receiptSessionDraftBuilder,
            Clock clock,
            @Value("${MONEYFLOW_RECEIPT_MAX_IMAGE_BYTES:8388608}") long maxImageBytes) {
        this.receiptSessionRepository = receiptSessionRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.receiptTextParser = receiptTextParser;
        this.receiptSessionDraftRepository = receiptSessionDraftRepository;
        this.receiptSessionDraftBuilder = receiptSessionDraftBuilder;
        this.clock = clock;
        this.maxImageBytes = Math.max(1, maxImageBytes);
    }

    @Transactional
    public ReceiptSessionDetailResponse create(UUID workspaceId, ReceiptSessionCreateRequest req, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        ReceiptSession session = ReceiptSession.builder()
                .workspace(member.getWorkspace())
                .createdByUser(user)
                .source(req == null || req.getSource() == null ? ReceiptSessionSource.UPLOAD : req.getSource())
                .note(normalize(req == null ? null : req.getNote()))
                .currency(currency(member.getWorkspace()))
                .warningsJson(writeWarnings(List.of(warning("OCR_NOT_REQUESTED"))))
                .build();
        return detail(receiptSessionRepository.save(session));
    }

    @Transactional
    public ReceiptSessionDetailResponse buildDrafts(UUID workspaceId, UUID sessionId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        ReceiptSession session = session(workspaceId, sessionId);
        String normalized = normalizeOcrText(session.getNormalizedOcrText());
        if (session.getOcrStatus() != ReceiptSessionOcrStatus.SUCCEEDED || normalized == null) {
            session.setWarningsJson(writeWarnings(List.of(warning("RECEIPT_OCR_REQUIRED"))));
            return detail(receiptSessionRepository.save(session));
        }
        receiptSessionDraftRepository.deleteByReceiptSessionId(session.getId());
        ReceiptSessionDraft draft = receiptSessionDraftBuilder.build(session);
        receiptSessionDraftRepository.save(draft);
        session.setStatus(draft.getStatus() == ReceiptSessionDraftStatus.NEEDS_REVIEW
                ? ReceiptSessionStatus.NEEDS_REVIEW
                : ReceiptSessionStatus.DRAFTED);
        session.setWarningsJson(writeWarnings(readWarnings(draft.getWarningsJson())));
        return detail(receiptSessionRepository.save(session));
    }

    @Transactional
    public ReceiptSessionDetailResponse uploadImage(UUID workspaceId, UUID sessionId, MultipartFile file, UUID userId) {
        requireActiveMember(workspaceId, userId);
        ReceiptSession session = session(workspaceId, sessionId);
        applyMetadata(session, file);
        validateImage(session, file);

        if (!storageService.isEnabled()) {
            session.setStatus(ReceiptSessionStatus.IMAGE_UPLOADED);
            session.setImageStorageStatus(ReceiptImageStorageStatus.STORAGE_NOT_CONFIGURED);
            session.setWarningsJson(writeWarnings(List.of(warning("RECEIPT_STORAGE_NOT_CONFIGURED"), warning("OCR_NOT_REQUESTED"))));
            return detail(receiptSessionRepository.save(session));
        }

        try {
            StoredReceiptImage stored = storageService.upload(objectKey(session, file), file);
            session.setStatus(ReceiptSessionStatus.IMAGE_UPLOADED);
            session.setImageStorageStatus(ReceiptImageStorageStatus.STORED);
            session.setImageStoragePublicId(stored.publicId());
            session.setImageUrl(stored.url());
            session.setWarningsJson(writeWarnings(List.of(warning("OCR_NOT_REQUESTED"))));
        } catch (BusinessException ex) {
            session.setStatus(ReceiptSessionStatus.IMAGE_UPLOADED);
            session.setImageStorageStatus(ReceiptImageStorageStatus.STORAGE_FAILED);
            session.setWarningsJson(writeWarnings(List.of(warning("RECEIPT_STORAGE_FAILED"), warning("OCR_NOT_REQUESTED"))));
        }
        return detail(receiptSessionRepository.save(session));
    }

    @Transactional
    public ReceiptSessionDetailResponse runOcr(UUID workspaceId, UUID sessionId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        ReceiptSession session = session(workspaceId, sessionId);
        if (session.getImageContentType() == null || session.getImageSizeBytes() == null || session.getImageSizeBytes() <= 0) {
            session.setOcrStatus(ReceiptSessionOcrStatus.NOT_REQUESTED);
            session.setWarningsJson(writeWarnings(List.of(warning("RECEIPT_IMAGE_REQUIRED"))));
            return detail(receiptSessionRepository.save(session));
        }

        ReceiptOcrResult result = ocrService.extractText(List.of(new ReceiptImageInput(
                0,
                session.getImageOriginalFilename(),
                session.getImageContentType(),
                session.getImageSizeBytes(),
                new byte[0],
                session.getImageUrl())));
        session.setOcrProvider(result.provider().name());

        if (result.status() == ReceiptOcrStatus.DISABLED) {
            session.setOcrStatus(ReceiptSessionOcrStatus.NOT_CONFIGURED);
            session.setWarningsJson(writeWarnings(List.of(warning("OCR_NOT_CONFIGURED"))));
            return detail(receiptSessionRepository.save(session));
        }
        if (result.status() == ReceiptOcrStatus.UNSUPPORTED) {
            session.setOcrStatus(ReceiptSessionOcrStatus.FAILED);
            session.setWarningsJson(writeWarnings(codes(result, "OCR_PROVIDER_NOT_IMPLEMENTED")));
            return detail(receiptSessionRepository.save(session));
        }
        if (result.status() == ReceiptOcrStatus.TEXT_EMPTY) {
            session.setOcrStatus(ReceiptSessionOcrStatus.FAILED);
            session.setWarningsJson(writeWarnings(List.of(warning("OCR_EMPTY_TEXT"))));
            return detail(receiptSessionRepository.save(session));
        }
        if (result.status() == ReceiptOcrStatus.TIMEOUT) {
            session.setOcrStatus(ReceiptSessionOcrStatus.FAILED);
            session.setWarningsJson(writeWarnings(List.of(warning("OCR_PROVIDER_TIMEOUT"))));
            return detail(receiptSessionRepository.save(session));
        }
        if (result.status() != ReceiptOcrStatus.SUCCEEDED && result.status() != ReceiptOcrStatus.EXTRACTED) {
            session.setOcrStatus(ReceiptSessionOcrStatus.FAILED);
            session.setWarningsJson(writeWarnings(codes(result, "OCR_PROVIDER_FAILED")));
            return detail(receiptSessionRepository.save(session));
        }

        String normalized = normalizeOcrText(result.text());
        if (normalized == null) {
            session.setOcrStatus(ReceiptSessionOcrStatus.FAILED);
            session.setWarningsJson(writeWarnings(List.of(warning("OCR_EMPTY_TEXT"))));
            return detail(receiptSessionRepository.save(session));
        }
        ReceiptTextParser.ParsedReceipt parsed = receiptTextParser.parse(normalized);
        session.setOcrStatus(ReceiptSessionOcrStatus.SUCCEEDED);
        session.setRawOcrText(result.text());
        session.setNormalizedOcrText(normalized);
        session.setMerchantName(result.merchantName() == null ? parsed.merchantName() : result.merchantName());
        session.setReceiptDate(result.receiptDate() == null ? parsed.receiptDate() : result.receiptDate());
        session.setTotalAmount(result.totalAmount() == null ? parsed.totalAmount() : result.totalAmount());
        session.setCurrency(result.currency() == null ? (session.getCurrency() == null ? "VND" : session.getCurrency()) : result.currency());
        session.setWarningsJson(writeWarnings(codesOrEmpty(result)));
        return detail(receiptSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public ReceiptSessionDetailResponse get(UUID workspaceId, UUID sessionId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return detail(session(workspaceId, sessionId));
    }

    private void validateImage(ReceiptSession session, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            failUpload(session, ReceiptImageStorageStatus.NOT_REQUESTED, "RECEIPT_IMAGE_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxImageBytes) {
            failUpload(session, ReceiptImageStorageStatus.FILE_TOO_LARGE, "OCR_FILE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        if (!ALLOWED_IMAGE_TYPES.contains(contentType(file))) {
            failUpload(session, ReceiptImageStorageStatus.UNSUPPORTED_FORMAT, "OCR_UNSUPPORTED_IMAGE_FORMAT", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private void failUpload(ReceiptSession session, ReceiptImageStorageStatus status, String code, HttpStatus httpStatus) {
        session.setStatus(ReceiptSessionStatus.FAILED);
        session.setImageStorageStatus(status);
        session.setWarningsJson(writeWarnings(List.of(warning(code))));
        receiptSessionRepository.save(session);
        throw new BusinessException(code, message(code), httpStatus);
    }

    private void applyMetadata(ReceiptSession session, MultipartFile file) {
        if (file == null) return;
        session.setImageOriginalFilename(file.getOriginalFilename());
        session.setImageContentType(contentType(file));
        session.setImageSizeBytes(file.getSize());
        session.setImageStoragePublicId(null);
        session.setImageUrl(null);
    }

    private ReceiptSessionDetailResponse detail(ReceiptSession session) {
        List<ReceiptSessionWarningResponse> warnings = readWarnings(session.getWarningsJson());
        List<ReceiptSessionDraftResponse> drafts = receiptSessionDraftRepository.findAllByReceiptSessionIdOrderByDraftIndexAsc(session.getId()).stream()
                .map(this::draftResponse)
                .toList();
        return ReceiptSessionDetailResponse.builder()
                .id(session.getId())
                .workspaceId(session.getWorkspace().getId())
                .status(session.getStatus())
                .imageStorageStatus(session.getImageStorageStatus())
                .imageContentType(session.getImageContentType())
                .imageOriginalFilename(session.getImageOriginalFilename())
                .imageSizeBytes(session.getImageSizeBytes())
                .imageUrl(session.getImageUrl())
                .ocrStatus(session.getOcrStatus())
                .ocrProvider(session.getOcrProvider())
                .rawOcrText(session.getRawOcrText())
                .normalizedOcrText(session.getNormalizedOcrText())
                .merchantName(session.getMerchantName())
                .receiptDate(session.getReceiptDate())
                .totalAmount(session.getTotalAmount())
                .currency(session.getCurrency())
                .drafts(drafts)
                .warnings(warnings)
                .nextActions(nextActions(session, drafts))
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ReceiptSessionDraftResponse draftResponse(ReceiptSessionDraft draft) {
        return ReceiptSessionDraftResponse.builder()
                .draftId(draft.getId())
                .draftIndex(draft.getDraftIndex())
                .type(draft.getType())
                .status(draft.getStatus())
                .amount(draft.getAmount())
                .currency(draft.getCurrency())
                .transactionDate(draft.getTransactionDate())
                .walletId(draft.getWalletId())
                .categoryId(draft.getCategoryId())
                .categoryHint(draft.getCategoryHint())
                .merchantName(draft.getMerchantName())
                .note(draft.getNote())
                .sourceText(draft.getSourceText())
                .confidence(draft.getConfidence())
                .warnings(readWarnings(draft.getWarningsJson()))
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    private List<String> nextActions(ReceiptSession session, List<ReceiptSessionDraftResponse> drafts) {
        if (session.getImageContentType() == null) return List.of("UPLOAD_IMAGE");
        if (!drafts.isEmpty()) return List.of("REVIEW_DRAFT");
        if (session.getOcrStatus() == ReceiptSessionOcrStatus.SUCCEEDED) return List.of("BUILD_DRAFT");
        if (session.getImageStorageStatus() == ReceiptImageStorageStatus.STORAGE_FAILED) return List.of("RETRY_UPLOAD", "RUN_OCR");
        return List.of("RUN_OCR");
    }

    private ReceiptSession session(UUID workspaceId, UUID sessionId) {
        return receiptSessionRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(sessionId, workspaceId)
                .orElseThrow(() -> new BusinessException("RECEIPT_SESSION_NOT_FOUND", "Receipt session not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private String objectKey(ReceiptSession session, MultipartFile file) {
        Instant now = Instant.now(clock);
        String ext = extension(file);
        return "receipts/" + MONTH.withZone(ZoneId.of("UTC")).format(now) + "/"
                + DAY.withZone(ZoneId.of("UTC")).format(now) + "/" + session.getId() + ext;
    }

    private String extension(MultipartFile file) {
        return switch (contentType(file)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String contentType(MultipartFile file) {
        String value = file == null ? null : file.getContentType();
        if (value == null || value.isBlank()) return "application/octet-stream";
        return value.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private String currency(Workspace workspace) {
        return workspace.getCurrency() == null ? "VND" : workspace.getCurrency().strip().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeOcrText(String value) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n').strip(), Normalizer.Form.NFC);
        normalized = MANY_BLANK_LINES.matcher(normalized).replaceAll("\n\n");
        return normalized.isBlank() ? null : normalized;
    }

    private List<ReceiptSessionWarningResponse> codes(ReceiptOcrResult result, String fallback) {
        if (result.warnings() == null || result.warnings().isEmpty()) return List.of(warning(fallback));
        return result.warnings().stream()
                .map(item -> warning(mapOcrCode(item.getCode(), fallback)))
                .toList();
    }

    private List<ReceiptSessionWarningResponse> codesOrEmpty(ReceiptOcrResult result) {
        if (result.warnings() == null || result.warnings().isEmpty()) return List.of();
        return result.warnings().stream()
                .map(item -> warning(mapOcrCode(item.getCode(), "OCR_PROVIDER_FAILED")))
                .toList();
    }

    private String mapOcrCode(String code, String fallback) {
        if ("RECEIPT_OCR_NOT_CONFIGURED".equals(code)) return "OCR_NOT_CONFIGURED";
        if ("RECEIPT_OCR_TEXT_EMPTY".equals(code)) return "OCR_EMPTY_TEXT";
        if ("RECEIPT_OCR_TIMEOUT".equals(code)) return "OCR_PROVIDER_TIMEOUT";
        if ("RECEIPT_OCR_FAILED".equals(code) || "RECEIPT_OCR_SERVICE_UNAVAILABLE".equals(code)) return "OCR_PROVIDER_FAILED";
        return code == null || code.isBlank() ? fallback : code;
    }

    private String writeWarnings(List<ReceiptSessionWarningResponse> warnings) {
        List<String> codes = warnings.stream().map(ReceiptSessionWarningResponse::getCode).distinct().toList();
        return String.join(",", codes);
    }

    private List<ReceiptSessionWarningResponse> readWarnings(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<ReceiptSessionWarningResponse> warnings = new ArrayList<>();
        for (String code : value.split(",")) {
            String trimmed = code.trim();
            if (!trimmed.isBlank()) warnings.add(warning(trimmed));
        }
        return warnings;
    }

    private ReceiptSessionWarningResponse warning(String code) {
        return ReceiptSessionWarningResponse.builder()
                .code(code)
                .message(message(code))
                .build();
    }

    private String message(String code) {
        return switch (code) {
            case "RECEIPT_IMAGE_REQUIRED" -> "Receipt image is required.";
            case "RECEIPT_STORAGE_NOT_CONFIGURED" -> "Receipt image storage is not configured.";
            case "RECEIPT_STORAGE_FAILED" -> "Receipt image storage failed.";
            case "OCR_NOT_CONFIGURED" -> "Receipt OCR is not configured.";
            case "OCR_PROVIDER_NOT_IMPLEMENTED" -> "Receipt OCR provider is not implemented yet.";
            case "OCR_PROVIDER_FAILED" -> "Receipt OCR provider failed.";
            case "OCR_PROVIDER_BAD_REQUEST" -> "Receipt OCR provider rejected the image.";
            case "OCR_EMPTY_TEXT" -> "Receipt OCR returned no text.";
            case "OCR_LOW_CONFIDENCE" -> "Receipt OCR confidence is low.";
            case "OCR_PROVIDER_TIMEOUT" -> "Receipt OCR provider timed out.";
            case "OCR_UNSUPPORTED_IMAGE_FORMAT" -> "Receipt image format is unsupported.";
            case "OCR_PROVIDER_AUTH_FAILED" -> "Receipt OCR provider authentication failed.";
            case "OCR_PROVIDER_RATE_LIMITED" -> "Receipt OCR provider is rate limited.";
            case "OCR_IMAGE_NOT_ACCESSIBLE" -> "Receipt image is not accessible to OCR.";
            case "OCR_TOTAL_NOT_FOUND" -> "Receipt total was not found.";
            case "OCR_DATE_NOT_FOUND" -> "Receipt date was not found.";
            case "OCR_FILE_TOO_LARGE" -> "Receipt image is too large.";
            case "OCR_NOT_REQUESTED" -> "OCR has not been requested.";
            case "RECEIPT_OCR_REQUIRED" -> "Receipt OCR is required before building drafts.";
            case "RECEIPT_OCR_EMPTY_TEXT" -> "Receipt OCR text is empty.";
            case "RECEIPT_DRAFT_MISSING_AMOUNT" -> "Receipt draft is missing amount.";
            case "RECEIPT_DRAFT_MISSING_WALLET" -> "Receipt draft needs a wallet before confirm.";
            case "RECEIPT_DRAFT_MISSING_CATEGORY" -> "Receipt draft needs a category before confirm.";
            case "RECEIPT_DATE_NOT_FOUND" -> "Receipt date was not found.";
            case "RECEIPT_DATE_AMBIGUOUS" -> "Receipt date is ambiguous.";
            case "RECEIPT_TOTAL_INFERRED" -> "Receipt total was inferred from OCR text.";
            case "RECEIPT_CATEGORY_HINT_ONLY" -> "Receipt category is a hint only.";
            case "RECEIPT_MERCHANT_NOT_FOUND" -> "Receipt merchant was not found.";
            default -> "Receipt session needs review.";
        };
    }
}
