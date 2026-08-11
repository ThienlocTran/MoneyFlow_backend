package com.moneyflowbackend;

import com.moneyflowbackend.diagnostics.service.RuntimeDiagnosticsService;
import com.moneyflowbackend.profile.avatar.DisabledAvatarStorageService;
import com.moneyflowbackend.receipt.ocr.ReceiptOcrProperties;
import com.moneyflowbackend.voice.asr.VoiceAsrProperties;
import com.moneyflowbackend.voice.storage.DisabledVoiceAudioStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeDiagnosticsServiceTests {
    @Test
    void missingCloudinaryConfigReportsNotConfiguredAndNoSecrets() throws Exception {
        RuntimeDiagnosticsService service = new RuntimeDiagnosticsService(
                readyDataSource(),
                new MockEnvironment().withProperty("spring.profiles.active", "dev"),
                new DisabledVoiceAudioStorageService(),
                new DisabledAvatarStorageService(),
                new ReceiptOcrProperties("none", 5, 5242880, 30, ""),
                new VoiceAsrProperties("external_http", "https://secret.example/asr?token=hidden", "", "", 60, 60, 0.8, 26214400, "vi", false,
                        "audio/webm,audio/ogg,audio/wav,audio/mpeg,audio/mp4,audio/x-m4a"),
                "cloudinary",
                "cloudinary",
                "",
                "",
                "",
                "",
                10485760,
                2097152);

        var response = service.diagnostics(true);

        assertThat(response.storage().voiceAudio().configured()).isFalse();
        assertThat(response.storage().voiceAudio().cloudNamePresent()).isFalse();
        assertThat(response.storage().voiceAudio().apiKeyPresent()).isFalse();
        assertThat(response.storage().voiceAudio().apiSecretPresent()).isFalse();
        assertThat(response.storage().avatar().configured()).isFalse();
        assertThat(response.storage().receiptOcr().provider()).isEqualTo("NONE");
        assertThat(response.storage().receiptOcr().enabled()).isFalse();
        assertThat(response.storage().receiptOcr().configured()).isFalse();
        assertThat(response.storage().receiptOcr().timeoutSeconds()).isEqualTo(30);
        assertThat(response.storage().receiptOcr().language()).isEqualTo("vi");
        assertThat(response.storage().receiptOcr().serviceUrlConfigured()).isFalse();
        assertThat(response.storage().voiceAsr().provider()).isEqualTo("EXTERNAL_HTTP");
        assertThat(response.storage().voiceAsr().enabled()).isTrue();
        assertThat(response.storage().voiceAsr().configured()).isTrue();
        assertThat(response.storage().voiceAsr().serviceUrlConfigured()).isTrue();
        assertThat(response.storage().voiceAsr().timeoutSeconds()).isEqualTo(60);
        assertThat(response.storage().voiceAsr().language()).isEqualTo("vi");
        assertThat(response.storage().voiceAsr().maxFileBytes()).isEqualTo(26214400);
    }

    private DataSource readyDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        return dataSource;
    }
}
