ALTER TABLE voice_records
    ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX uq_voice_records_workspace_user_idempotency
    ON voice_records (workspace_id, created_by_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
