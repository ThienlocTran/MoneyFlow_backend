ALTER TABLE voice_records
    ADD COLUMN IF NOT EXISTS audio_storage_provider VARCHAR(50),
    ADD COLUMN IF NOT EXISTS audio_storage_key TEXT,
    ADD COLUMN IF NOT EXISTS audio_mime_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS audio_size_bytes BIGINT CHECK (audio_size_bytes IS NULL OR audio_size_bytes >= 0),
    ADD COLUMN IF NOT EXISTS audio_uploaded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS audio_deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS audio_duration_ms INTEGER CHECK (audio_duration_ms IS NULL OR audio_duration_ms >= 0),
    ADD COLUMN IF NOT EXISTS audio_upload_status VARCHAR(30);
