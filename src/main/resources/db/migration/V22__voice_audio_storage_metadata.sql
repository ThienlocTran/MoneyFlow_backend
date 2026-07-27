ALTER TABLE voice_records
    ADD COLUMN storage_provider VARCHAR(40),
    ADD COLUMN storage_key VARCHAR(500),
    ADD COLUMN audio_uploaded_at TIMESTAMPTZ,
    ADD COLUMN audio_deleted_at TIMESTAMPTZ;

UPDATE voice_records
SET storage_provider = split_part(audio_url, ':', 1),
    storage_key = storage_public_id
WHERE storage_public_id IS NOT NULL
  AND storage_provider IS NULL;
