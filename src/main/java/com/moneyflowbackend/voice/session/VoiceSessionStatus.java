package com.moneyflowbackend.voice.session;

public enum VoiceSessionStatus {
    CREATED,
    AUDIO_UPLOADED,
    TRANSCRIBING,
    TRANSCRIBED,
    INTERPRETED,
    NEEDS_REVIEW,
    PARTIALLY_CONFIRMED,
    CONFIRMED,
    FAILED,
    CANCELLED
}
