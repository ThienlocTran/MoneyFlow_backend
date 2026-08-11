package com.moneyflowbackend.voice.session;

public enum VoiceSessionAsrStatus {
    NOT_REQUESTED,
    PENDING,
    TRANSCRIBING,
    SUCCEEDED,
    LOW_CONFIDENCE,
    NO_SPEECH,
    FAILED,
    TIMEOUT
}
