ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS voice_session_id UUID;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS voice_session_draft_id UUID;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_voice_session
        FOREIGN KEY (voice_session_id) REFERENCES voice_sessions(id) ON DELETE SET NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_voice_session_draft
        FOREIGN KEY (voice_session_draft_id) REFERENCES voice_session_drafts(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_voice_session
    ON transactions(voice_session_id);

CREATE INDEX IF NOT EXISTS idx_transactions_voice_session_draft
    ON transactions(voice_session_draft_id);
