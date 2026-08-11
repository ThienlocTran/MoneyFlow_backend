package com.moneyflowbackend.receipt.session;

import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;
import com.moneyflowbackend.receipt.service.ReceiptTextParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReceiptSessionDraftBuilder {
    private final ReceiptTextParser receiptTextParser;

    public ReceiptSessionDraftBuilder(ReceiptTextParser receiptTextParser) {
        this.receiptTextParser = receiptTextParser;
    }

    public ReceiptSessionDraft build(ReceiptSession session) {
        String text = session.getNormalizedOcrText();
        ReceiptTextParser.ParsedReceipt parsed = receiptTextParser.parse(text);
        BigDecimal amount = parsed.totalAmount() != null && !parsed.totalInferred() ? parsed.totalAmount() : first(session.getTotalAmount(), parsed.totalAmount());
        String merchant = merchant(session.getMerchantName(), parsed.merchantName());
        String categoryHint = categoryHint(text);
        List<String> warnings = new ArrayList<>();
        if (amount == null) warnings.add("RECEIPT_DRAFT_MISSING_AMOUNT");
        else if (session.getTotalAmount() == null && parsed.totalInferred()) warnings.add("RECEIPT_TOTAL_INFERRED");
        if (session.getReceiptDate() == null && parsed.receiptDate() == null) warnings.add("RECEIPT_DATE_NOT_FOUND");
        if (merchant == null) warnings.add("RECEIPT_MERCHANT_NOT_FOUND");
        warnings.add("RECEIPT_DRAFT_MISSING_WALLET");
        if (categoryHint == null) warnings.add("RECEIPT_CATEGORY_LOW_CONFIDENCE");
        else warnings.add("RECEIPT_CATEGORY_HINT_ONLY");

        return ReceiptSessionDraft.builder()
                .receiptSession(session)
                .workspaceId(session.getWorkspace().getId())
                .draftIndex(0)
                .type("EXPENSE")
                .status(warnings.isEmpty() ? ReceiptSessionDraftStatus.DRAFT : ReceiptSessionDraftStatus.NEEDS_REVIEW)
                .amount(amount)
                .currency(first(session.getCurrency(), "VND"))
                .transactionDate(session.getReceiptDate() == null ? parsed.receiptDate() : session.getReceiptDate())
                .categoryHint(categoryHint)
                .merchantName(merchant)
                .note(merchant == null ? "Hoa don OCR" : "Hoa don: " + merchant)
                .sourceText(sourceText(text))
                .warningsJson(String.join(",", warnings))
                .build();
    }

    private String categoryHint(String text) {
        String value = VietnameseTextNormalizer.comparable(text);
        if (value.contains("xang") || value.contains("petrol") || value.contains("fuel")) return "Xăng xe";
        if (value.contains("ca phe") || value.contains("cafe") || value.contains("coffee") || value.contains("tra sua")) return "Cà phê";
        if (value.contains("restaurant") || value.contains("quan ") || value.contains("food")) return "Ăn uống";
        if (value.contains("bach hoa xanh") || value.contains("sieu thi") || value.contains("grocery") || value.contains("mart")) return "Di cho";
        if (value.contains("thuoc") || value.contains("pharmacy")) return "Y tế";
        if (value.contains("dien") || value.contains("nuoc") || value.contains("internet")) return "Tiện ích";
        return null;
    }

    private String sourceText(String text) {
        if (text == null) return null;
        String concise = String.join("\n", text.lines().map(String::strip).filter(line -> !line.isBlank()).limit(8).toList());
        return concise.length() <= 1000 ? concise : concise.substring(0, 1000);
    }

    private String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal first(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private String merchant(String value, String fallback) {
        String merchant = first(value, null);
        if (merchant == null) return fallback;
        String normalized = VietnameseTextNormalizer.comparable(merchant);
        return normalized.matches(".*\\b\\d{1,2}:\\d{2}\\b.*") || normalized.matches(".*\\b\\d{5,}\\b.*") ? fallback : merchant;
    }
}
