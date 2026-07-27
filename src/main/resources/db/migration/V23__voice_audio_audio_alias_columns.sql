ALTER TABLE voice_records
    ADD COLUMN IF NOT EXISTS audio_storage_provider VARCHAR(40),
    ADD COLUMN IF NOT EXISTS audio_storage_key VARCHAR(500);

UPDATE voice_records
SET audio_storage_provider = storage_provider,
    audio_storage_key = storage_key
WHERE audio_storage_key IS NULL
  AND storage_key IS NOT NULL;
