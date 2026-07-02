ALTER TABLE voice_records
    DROP CONSTRAINT IF EXISTS voice_records_voice_status_check;

ALTER TABLE voice_records
    ADD CONSTRAINT voice_records_voice_status_check
        CHECK (voice_status IN ('DRAFT', 'UPLOADED', 'AUDIO_STORED', 'TRANSCRIBED', 'PARSED', 'CONFIRMED', 'STORAGE_FAILED', 'FAILED', 'AUDIO_DELETED', 'DELETED'));
