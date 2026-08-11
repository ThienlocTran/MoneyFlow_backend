CREATE TABLE IF NOT EXISTS receipt_sessions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    source VARCHAR(30) NOT NULL DEFAULT 'UPLOAD',
    note TEXT,
    image_storage_status VARCHAR(40) NOT NULL DEFAULT 'NOT_REQUESTED',
    image_content_type VARCHAR(100),
    image_original_filename VARCHAR(255),
    image_size_bytes BIGINT,
    image_storage_public_id TEXT,
    image_url TEXT,
    ocr_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUESTED',
    ocr_provider VARCHAR(40),
    raw_ocr_text TEXT,
    normalized_ocr_text TEXT,
    merchant_name TEXT,
    receipt_date DATE,
    total_amount NUMERIC(19,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    warnings_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_receipt_sessions_workspace_created
    ON receipt_sessions(workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_receipt_sessions_workspace_status
    ON receipt_sessions(workspace_id, status);
