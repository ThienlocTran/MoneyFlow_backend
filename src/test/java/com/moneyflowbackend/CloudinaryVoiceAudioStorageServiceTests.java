package com.moneyflowbackend;

import com.moneyflowbackend.voice.storage.CloudinaryVoiceAudioStorageService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryVoiceAudioStorageServiceTests {
    @Test
    void playbackUrlUsesShortLivedAuthenticatedDownloadUrl() {
        CloudinaryVoiceAudioStorageService service = new CloudinaryVoiceAudioStorageService(
                null,
                Clock.fixed(Instant.parse("2026-06-15T01:00:00Z"), ZoneOffset.UTC),
                "demo-cloud",
                "api-key",
                "api-secret",
                "moneyflow/voice");

        var playback = service.playbackUrl("moneyflow/voice/workspaces/ws/voice/record", "audio/webm");

        assertThat(playback.playbackUrl()).startsWith("https://api.cloudinary.com/v1_1/demo-cloud/video/download?");
        assertThat(playback.playbackUrl()).contains("type=authenticated");
        assertThat(playback.playbackUrl()).contains("expires_at=1781485500");
        assertThat(playback.playbackUrl()).contains("signature=");
        assertThat(playback.playbackUrl()).doesNotContain("api-secret");
        assertThat(playback.expiresAt()).isEqualTo(Instant.parse("2026-06-15T01:05:00Z"));
    }
}
