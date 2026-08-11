package com.moneyflowbackend.receipt.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptDateExtractorTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);
    private final ReceiptDateExtractor extractor = new ReceiptDateExtractor();

    @Test
    void extractsDdMmYyyyFromReceiptCodeLine() {
        ReceiptDateExtractor.RankedReceiptDate ranked = extractor.rank("So CT: OV204 - 30/07/2026 19:46", clock);

        assertThat(ranked.primary()).isNotNull();
        assertThat(ranked.primary().value()).isEqualTo(LocalDate.of(2026, 7, 30));
    }

    @Test
    void rejectsPhoneOrHotlineAsDate() {
        ReceiptDateExtractor.RankedReceiptDate ranked = extractor.rank("Gop y: 18001067", clock);

        assertThat(ranked.primary()).isNull();
    }

    @Test
    void azureStructuredDateWinsWhenHighConfidence() {
        ReceiptDateExtractor.RankedReceiptDate ranked = extractor.rank("Ngay 30/07/2026", LocalDate.of(2026, 8, 1), 0.9, clock);

        assertThat(ranked.primary().value()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(ranked.primary().source()).isEqualTo(ReceiptDateCandidate.Source.AZURE_STRUCTURED_DATE);
    }

    @Test
    void azureLowConfidenceFallsBackToTextDate() {
        ReceiptDateExtractor.RankedReceiptDate ranked = extractor.rank("Ngay 30/07/2026", LocalDate.of(2026, 8, 1), 0.4, clock);

        assertThat(ranked.primary().value()).isEqualTo(LocalDate.of(2026, 7, 30));
    }
}
