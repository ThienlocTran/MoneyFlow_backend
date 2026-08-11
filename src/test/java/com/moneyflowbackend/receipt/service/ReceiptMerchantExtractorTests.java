package com.moneyflowbackend.receipt.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptMerchantExtractorTests {
    private final ReceiptMerchantExtractor extractor = new ReceiptMerchantExtractor();

    @Test
    void rejectsTimeAsMerchant() {
        ReceiptMerchantExtractor.RankedReceiptMerchant ranked = extractor.rank("16:29 G");

        assertThat(ranked.primary()).isNull();
        assertThat(ranked.warnings()).contains("RECEIPT_MERCHANT_NOT_FOUND", "RECEIPT_MERCHANT_REJECTED_CONTEXT");
    }

    @Test
    void normalizesBachHoaXanhHeader() {
        ReceiptMerchantExtractor.RankedReceiptMerchant ranked = extractor.rank("PHIEU THANH TOAN BACH HOA XANH");

        assertThat(ranked.primary()).isNotNull();
        assertThat(ranked.primary().value()).isEqualTo("Bách Hóa Xanh");
        assertThat(ranked.primary().confidence()).isEqualTo(ReceiptMerchantCandidate.Confidence.HIGH);
    }

    @Test
    void azureStructuredMerchantWinsWhenHighConfidence() {
        ReceiptMerchantExtractor.RankedReceiptMerchant ranked = extractor.rank("PHIEU THANH TOAN BACH HOA XANH", "WinMart", 0.91);

        assertThat(ranked.primary().value()).isEqualTo("WinMart");
        assertThat(ranked.primary().source()).isEqualTo(ReceiptMerchantCandidate.Source.AZURE_STRUCTURED_MERCHANT);
    }

    @Test
    void azureLowConfidenceFallsBackToText() {
        ReceiptMerchantExtractor.RankedReceiptMerchant ranked = extractor.rank("PHIEU THANH TOAN BACH HOA XANH", "16:29 G", 0.40);

        assertThat(ranked.primary().value()).isEqualTo("Bách Hóa Xanh");
    }
}
