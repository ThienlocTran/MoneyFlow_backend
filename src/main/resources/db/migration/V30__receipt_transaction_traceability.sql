ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS receipt_session_id UUID;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS receipt_session_draft_id UUID;

ALTER TABLE receipt_session_drafts
    ADD COLUMN IF NOT EXISTS confirmed_entity_type VARCHAR(30);

ALTER TABLE receipt_session_drafts
    ADD COLUMN IF NOT EXISTS confirmed_entity_id UUID;

ALTER TABLE receipt_session_drafts
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMPTZ;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_receipt_session
        FOREIGN KEY (receipt_session_id) REFERENCES receipt_sessions(id) ON DELETE SET NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_receipt_session_draft
        FOREIGN KEY (receipt_session_draft_id) REFERENCES receipt_session_drafts(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_receipt_session
    ON transactions(receipt_session_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_receipt_session_draft
    ON transactions(receipt_session_draft_id)
    WHERE source_type = 'RECEIPT' AND receipt_session_draft_id IS NOT NULL;
