CREATE TABLE IF NOT EXISTS voice_sessions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    audio_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    asr_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUESTED',
    command_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUESTED',
    confirm_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIRMED',
    original_mime_type VARCHAR(100),
    storage_mime_type VARCHAR(100),
    duration_ms BIGINT,
    size_bytes BIGINT,
    playback_available BOOLEAN NOT NULL DEFAULT FALSE,
    transcript TEXT,
    normalized_transcript TEXT,
    transcript_confidence NUMERIC(6,4),
    asr_provider VARCHAR(40),
    asr_model VARCHAR(120),
    asr_language VARCHAR(20),
    asr_warnings_json TEXT,
    command_warnings_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS voice_session_drafts (
    id UUID PRIMARY KEY,
    voice_session_id UUID NOT NULL REFERENCES voice_sessions(id) ON DELETE CASCADE,
    draft_index INTEGER NOT NULL,
    source_text TEXT,
    normalized_source_text TEXT,
    type VARCHAR(60),
    amount NUMERIC(19,2),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    wallet_id UUID,
    category_id UUID,
    jar_id UUID,
    fund_id UUID,
    debt_id UUID,
    counterparty_id UUID,
    transaction_type VARCHAR(60),
    movement_type VARCHAR(60),
    affects_wallet_balance BOOLEAN,
    wallet_required BOOLEAN NOT NULL DEFAULT FALSE,
    category_required BOOLEAN NOT NULL DEFAULT FALSE,
    confirmable BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    warnings_json TEXT,
    confirmed_entity_type VARCHAR(60),
    confirmed_entity_id UUID,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_voice_sessions_workspace_created
    ON voice_sessions(workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_voice_session_drafts_session_index
    ON voice_session_drafts(voice_session_id, draft_index);
