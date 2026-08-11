package com.moneyflowbackend.receipt.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class ReceiptTextParser {
    private final ReceiptAmountRanker amountRanker;
    private final ReceiptMerchantExtractor merchantExtractor;
    private final ReceiptDateExtractor dateExtractor;
    private final Clock clock;

    public ReceiptTextParser(ReceiptAmountRanker amountRanker,
                             ReceiptMerchantExtractor merchantExtractor,
                             ReceiptDateExtractor dateExtractor,
                             Clock clock) {
        this.amountRanker = amountRanker;
        this.merchantExtractor = merchantExtractor;
        this.dateExtractor = dateExtractor;
        this.clock = clock;
    }

    public ParsedReceipt parse(String rawText) {
        return parse(rawText, null, null, null, null, null, null);
    }

    public ParsedReceipt parse(String rawText, BigDecimal structuredTotal, Double structuredTotalConfidence) {
        return parse(rawText, structuredTotal, structuredTotalConfidence, null, null, null, null);
    }

    public ParsedReceipt parse(String rawText,
                               BigDecimal structuredTotal,
                               Double structuredTotalConfidence,
                               String structuredMerchant,
                               Double structuredMerchantConfidence,
                               LocalDate structuredDate,
                               Double structuredDateConfidence) {
        String text = rawText == null ? "" : rawText.strip();
        ReceiptAmountRanker.RankedReceiptAmount rankedAmount = amountRanker.rank(text, structuredTotal, structuredTotalConfidence);
        ReceiptMerchantExtractor.RankedReceiptMerchant rankedMerchant = merchantExtractor.rank(text, structuredMerchant, structuredMerchantConfidence);
        ReceiptDateExtractor.RankedReceiptDate rankedDate = dateExtractor.rank(text, structuredDate, structuredDateConfidence, clock);
        ReceiptAmountCandidate total = rankedAmount.primary();
        boolean totalInferred = total != null && total.source() == ReceiptAmountCandidate.Source.TEXT_UNLABELED;
        return new ParsedReceipt(
                rankedMerchant.primary() == null ? null : rankedMerchant.primary().value(),
                rankedDate.primary() == null ? null : rankedDate.primary().value(),
                total == null ? null : total.value(),
                rankedAmount.candidates().stream()
                        .filter(candidate -> !candidate.excluded())
                        .map(ReceiptAmountCandidate::value)
                        .toList(),
                totalInferred,
                rankedAmount.candidates(),
                rankedAmount.warnings(),
                rankedMerchant.candidates(),
                rankedMerchant.warnings(),
                rankedDate.candidates(),
                rankedDate.warnings());
    }

    public record ParsedReceipt(
            String merchantName,
            LocalDate receiptDate,
            BigDecimal totalAmount,
            List<BigDecimal> lineAmounts,
            boolean totalInferred,
            List<ReceiptAmountCandidate> amountCandidates,
            List<String> amountWarnings,
            List<ReceiptMerchantCandidate> merchantCandidates,
            List<String> merchantWarnings,
            List<ReceiptDateCandidate> dateCandidates,
            List<String> dateWarnings) {
    }
}
