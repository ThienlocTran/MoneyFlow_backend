package com.moneyflowbackend;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.moneyflowbackend.voice.service.VoiceAudioRetentionScheduler;
import com.moneyflowbackend.voice.service.VoiceAudioService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceAudioRetentionSchedulerTests {
    @Test
    void scheduledCleanupCallsExpiredCleanupService() {
        VoiceAudioService service = mock(VoiceAudioService.class);
        VoiceAudioRetentionScheduler scheduler = new VoiceAudioRetentionScheduler(service);

        scheduler.deleteExpiredVoiceAudio();

        verify(service).deleteExpiredVoiceAudio();
    }

    @Test
    void cleanupLogsCountsOnly() {
        VoiceAudioService service = mock(VoiceAudioService.class);
        when(service.deleteExpiredVoiceAudio()).thenReturn(2);
        VoiceAudioRetentionScheduler scheduler = new VoiceAudioRetentionScheduler(service);
        ListAppender<ILoggingEvent> appender = appender();

        scheduler.deleteExpiredVoiceAudio();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("Voice audio retention cleanup completed: deleted=2");
        assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("transcript", "signature=", "storagePublicId");
    }

    @Test
    void cleanupFailureLogIsRedacted() {
        VoiceAudioService service = mock(VoiceAudioService.class);
        when(service.deleteExpiredVoiceAudio()).thenThrow(new RuntimeException("signature=abc transcript=private"));
        VoiceAudioRetentionScheduler scheduler = new VoiceAudioRetentionScheduler(service);
        ListAppender<ILoggingEvent> appender = appender();

        scheduler.deleteExpiredVoiceAudio();

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("abc");
    }

    private ListAppender<ILoggingEvent> appender() {
        Logger logger = (Logger) LoggerFactory.getLogger(VoiceAudioRetentionScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
