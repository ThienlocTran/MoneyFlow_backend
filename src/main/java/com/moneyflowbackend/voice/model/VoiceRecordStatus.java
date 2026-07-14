package com.moneyflowbackend.voice.model;

public enum VoiceRecordStatus {
    DRAFT,
    UPLOADED,
    AUDIO_STORED,
    TRANSCRIBED,
    PARSED,
    CONFIRMED,
    STORAGE_FAILED,
    FAILED,
    AUDIO_DELETED,
    DELETED
}
