package com.moneyflowbackend.receipt.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptAmountRankerTests {
    private final ReceiptAmountRanker ranker = new ReceiptAmountRanker();

    @Test
    void bachHoaXanhReceiptSelectsPayableTotal() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("""
                PHIEU THANH TOAN BACH HOA XANH
                So CT: OV204476607173192 - 30/07/2026 19:46-NV:195954
                SL Gia ban (co VAT) Thanh tien
                du du ruot do
                1,148 20.600 10.300 (VAT:5%) 11.824
                suon que heo nk
                0,806 65.000 39.000 (VAT:5%) 31.434
                ca loc lam sach
                0,235 103.000 (VAT:5%) 24.205
                Phai thanh toan: 67.463
                Diem su dung: 2.463
                Tien mat (Da lam tron): 65.000
                Tien khach dua: 200.000
                Tien thoi lai: 135.000
                Ma tra cuu: 6359148EDC
                Gop y: 18001067
                """);

        assertThat(ranked.primary()).isNotNull();
        assertThat(ranked.primary().value()).isEqualByComparingTo("67463");
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "65000", false, null));
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "200000", true, "CUSTOMER_TENDERED"));
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "135000", true, "CHANGE_RETURNED"));
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "2463", true, "LOYALTY_POINTS"));
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "6359148", true, "RECEIPT_CODE"));
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "18001067", true, "PHONE_OR_HOTLINE"));
        assertThat(ranked.primary().value()).isNotEqualByComparingTo("6359148");
        assertThat(ranked.primary().value()).isNotEqualByComparingTo("18001067");
    }

    @Test
    void largestNumberIsNotSelectedWhenPayableTotalExists() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("""
                Phai thanh toan: 67.463
                Ma tra cuu: 6359148EDC
                """);

        assertThat(ranked.primary().value()).isEqualByComparingTo("67463");
    }

    @Test
    void customerTenderedIsExcluded() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("Tien khach dua: 200.000");

        assertThat(ranked.primary()).isNull();
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "200000", true, "CUSTOMER_TENDERED"));
    }

    @Test
    void changeReturnedIsExcluded() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("Tien thoi lai: 135.000");

        assertThat(ranked.primary()).isNull();
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "135000", true, "CHANGE_RETURNED"));
    }

    @Test
    void loyaltyPointsAreExcluded() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("Diem su dung: 2.463");

        assertThat(ranked.primary()).isNull();
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "2463", true, "LOYALTY_POINTS"));
    }

    @Test
    void phoneOrHotlineIsExcluded() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("Gop y: 18001067");

        assertThat(ranked.primary()).isNull();
        assertThat(ranked.candidates()).anySatisfy(candidate -> assertCandidate(candidate, "18001067", true, "PHONE_OR_HOTLINE"));
    }

    @Test
    void roundedCashIsSecondaryWhenPayableTotalExists() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("""
                Phai thanh toan: 67.463
                Tien mat (Da lam tron): 65.000
                """);

        assertThat(ranked.primary().value()).isEqualByComparingTo("67463");
        assertThat(ranked.candidates()).anySatisfy(candidate -> {
            assertThat(candidate.value()).isEqualByComparingTo("65000");
            assertThat(candidate.source()).isEqualTo(ReceiptAmountCandidate.Source.TEXT_LABEL_MEDIUM);
        });
    }

    @Test
    void totalLabelVariantsAreSelected() {
        assertThat(ranker.rank("Tong cong: 12.000").primary().value()).isEqualByComparingTo("12000");
        assertThat(ranker.rank("Tong thanh toan: 13.000").primary().value()).isEqualByComparingTo("13000");
        assertThat(ranker.rank("Can thanh toan: 14.000").primary().value()).isEqualByComparingTo("14000");
        assertThat(ranker.rank("Thanh tien: 15.000").primary().value()).isEqualByComparingTo("15000");
    }

    @Test
    void quantityOrWeightIsNotPrimaryAmount() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("1,148 kg");

        assertThat(ranked.primary()).isNull();
    }

    @Test
    void dateOrTimeIsNotPrimaryAmount() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("30/07/2026 16:29");

        assertThat(ranked.primary()).isNull();
    }

    @Test
    void azureStructuredTotalWinsWhenConfidenceIsHigh() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("Phai thanh toan: 67.463", new BigDecimal("65000"), 0.91);

        assertThat(ranked.primary().value()).isEqualByComparingTo("65000");
        assertThat(ranked.primary().source()).isEqualTo(ReceiptAmountCandidate.Source.AZURE_STRUCTURED_TOTAL);
    }

    @Test
    void azureStructuredTotalLowConfidenceFallsBackToTextLabel() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("Phai thanh toan: 67.463", new BigDecimal("65000"), 0.40);

        assertThat(ranked.primary().value()).isEqualByComparingTo("67463");
        assertThat(ranked.primary().source()).isEqualTo(ReceiptAmountCandidate.Source.TEXT_LABEL_HIGH);
    }

    @Test
    void noSafeAmountReturnsNullInsteadOfLargestNumber() {
        ReceiptAmountRanker.RankedReceiptAmount ranked = ranker.rank("""
                Mon A 25.000
                Mon B 35.000
                """);

        assertThat(ranked.primary()).isNull();
        assertThat(ranked.warnings()).contains("RECEIPT_TOTAL_NOT_FOUND");
    }

    private void assertCandidate(ReceiptAmountCandidate candidate, String value, boolean excluded, String reason) {
        assertThat(candidate.value()).isEqualByComparingTo(value);
        assertThat(candidate.excluded()).isEqualTo(excluded);
        assertThat(candidate.excludedReason()).isEqualTo(reason);
    }
}
