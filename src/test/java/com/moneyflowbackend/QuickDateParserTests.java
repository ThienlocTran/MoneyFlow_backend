package com.moneyflowbackend;

import com.moneyflowbackend.quickentry.parser.QuickDateParser;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class QuickDateParserTests {
    private final QuickDateParser parser = new QuickDateParser(Clock.fixed(Instant.parse("2026-06-15T01:00:00Z"), ZoneOffset.UTC));

    @Test
    void parsesRelativeAndDefaultDatesInWorkspaceTimezone() {
        assertThat(parser.parse("an sang 35k", "Asia/Ho_Chi_Minh").date()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(parser.parse("hom qua an toi 80k", "Asia/Ho_Chi_Minh").date()).isEqualTo(LocalDate.of(2026, 6, 14));
        var tomorrow = parser.parse("mai dong tien tro 2tr", "Asia/Ho_Chi_Minh");
        assertThat(tomorrow.date()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(tomorrow.status()).isEqualTo(TransactionStatus.PLANNED);
        assertThat(tomorrow.future()).isTrue();
    }

    @Test
    void parsesExplicitDatesAndTimes() {
        assertThat(parser.parse("15/06 an sang", "Asia/Ho_Chi_Minh").date()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(parser.parse("15/06/2026 an sang", "Asia/Ho_Chi_Minh").date()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(parser.parse("2026-06-15 an sang", "Asia/Ho_Chi_Minh").date()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(parser.parse("8h cafe", "Asia/Ho_Chi_Minh").time()).isEqualTo(LocalTime.of(8, 0));
        assertThat(parser.parse("8h30 cafe", "Asia/Ho_Chi_Minh").time()).isEqualTo(LocalTime.of(8, 30));
        assertThat(parser.parse("20:15 cafe", "Asia/Ho_Chi_Minh").time()).isEqualTo(LocalTime.of(20, 15));
    }

    @Test
    void flagsInvalidDateOrTime() {
        assertThat(parser.parse("31/02 an sang", "Asia/Ho_Chi_Minh").invalidDate()).isTrue();
        assertThat(parser.parse("25:99 an sang", "Asia/Ho_Chi_Minh").invalidTime()).isTrue();
    }
}