CREATE TABLE IF NOT EXISTS receipt_session_drafts (
    id UUID PRIMARY KEY,
    receipt_session_id UUID NOT NULL REFERENCES receipt_sessions(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    draft_index INTEGER NOT NULL,
    type VARCHAR(30) NOT NULL DEFAULT 'EXPENSE',
    status VARCHAR(30) NOT NULL DEFAULT 'NEEDS_REVIEW',
    amount NUMERIC(19,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    transaction_date DATE,
    wallet_id UUID,
    category_id UUID,
    category_hint VARCHAR(255),
    merchant_name VARCHAR(255),
    note TEXT,
    source_text TEXT,
    confidence DOUBLE PRECISION,
    warnings_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_receipt_session_drafts_session_index
    ON receipt_session_drafts(receipt_session_id, draft_index);

CREATE INDEX IF NOT EXISTS idx_receipt_session_drafts_workspace
    ON receipt_session_drafts(workspace_id);
