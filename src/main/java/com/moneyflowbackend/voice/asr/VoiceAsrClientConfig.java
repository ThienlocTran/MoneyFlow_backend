package com.moneyflowbackend.voice.asr;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class VoiceAsrClientConfig {
    @Bean
    public VoiceAsrClient voiceAsrClient(VoiceAsrProperties properties) {
        return switch (properties.provider()) {
            case MOCK -> new MockVoiceAsrClient();
            case EXTERNAL_HTTP -> new ExternalHttpVoiceAsrClient(properties, HttpClient.newHttpClient());
            case NONE -> new NoopVoiceAsrClient();
        };
    }
}
