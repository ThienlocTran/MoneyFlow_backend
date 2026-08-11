ALTER TABLE voice_session_drafts
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING TRIM(currency)::VARCHAR(3);
