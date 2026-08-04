package com.moneyflowbackend.voice.session;

public enum VoiceSessionCommandStatus {
    NOT_REQUESTED,
    INTERPRETED,
    NEEDS_CLARIFICATION,
    NEEDS_REVIEW,
    UNSUPPORTED,
    FAILED
}
