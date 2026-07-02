CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(40) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(120) NOT NULL,
    avatar_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'LOCKED', 'DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_users_username_lower
    ON users (LOWER(username))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_users_email_lower
    ON users (LOWER(email))
    WHERE deleted_at IS NULL;

CREATE TABLE auth_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL
        CHECK (provider IN ('LOCAL', 'GOOGLE')),
    provider_subject VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_subject),
    UNIQUE (user_id, provider)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    workspace_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL'
        CHECK (workspace_type IN ('PERSONAL', 'SHARED', 'FAMILY', 'GROUP')),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    quick_amount_unit VARCHAR(20) NOT NULL DEFAULT 'THOUSAND'
        CHECK (quick_amount_unit IN ('UNIT', 'THOUSAND')),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE workspace_people (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    linked_user_id UUID REFERENCES users(id),
    display_name VARCHAR(120) NOT NULL,
    person_kind VARCHAR(20) NOT NULL
        CHECK (person_kind IN ('MEMBER', 'COUNTERPARTY')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_workspace_people_linked_user
    ON workspace_people (workspace_id, linked_user_id)
    WHERE linked_user_id IS NOT NULL;

CREATE TABLE workspace_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    person_id UUID REFERENCES workspace_people(id),
    role VARCHAR(20) NOT NULL
        CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (member_status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, user_id)
);

CREATE TABLE workspace_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    invited_by_user_id UUID NOT NULL REFERENCES users(id),
    invited_user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'EDITOR'
        CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    invitation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (invitation_status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_pending_workspace_invitation
    ON workspace_invitations (workspace_id, invited_user_id)
    WHERE invitation_status = 'PENDING';

CREATE TABLE jars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    allocation_percent NUMERIC(5,2)
        CHECK (allocation_percent IS NULL OR allocation_percent BETWEEN 0 AND 100),
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, code)
);

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    jar_id UUID REFERENCES jars(id),
    name VARCHAR(120) NOT NULL,
    category_type VARCHAR(20) NOT NULL
        CHECK (category_type IN ('INCOME', 'EXPENSE', 'SPECIAL')),
    icon VARCHAR(80),
    is_quick_action BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_categories_active_name
    ON categories (workspace_id, category_type, LOWER(name))
    WHERE is_active = TRUE;

CREATE TABLE category_keywords (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    keyword VARCHAR(120) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    is_user_learned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_category_keyword_lower
    ON category_keywords (workspace_id, LOWER(keyword));

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    wallet_type VARCHAR(20) NOT NULL
        CHECK (wallet_type IN ('CASH', 'BANK', 'E_WALLET', 'SAVING', 'OTHER')),
    opening_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    opening_date DATE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    include_in_total BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_wallet_name_active
    ON wallets (workspace_id, LOWER(name))
    WHERE is_active = TRUE;

CREATE UNIQUE INDEX uq_wallet_default
    ON wallets (workspace_id)
    WHERE is_default = TRUE AND is_active = TRUE;

CREATE TABLE wallet_balance_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    balance NUMERIC(19,2) NOT NULL,
    source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL'
        CHECK (source_type IN ('MANUAL', 'EXCEL_MIGRATION', 'SYSTEM')),
    source_reference VARCHAR(255),
    migration_key VARCHAR(64),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, migration_key)
);

CREATE TABLE voice_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    audio_url TEXT,
    storage_public_id VARCHAR(255),
    mime_type VARCHAR(100),
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    file_size_bytes BIGINT CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0),
    original_transcript TEXT,
    edited_transcript TEXT,
    voice_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (voice_status IN ('DRAFT', 'UPLOADED', 'TRANSCRIBED', 'PARSED', 'CONFIRMED', 'FAILED', 'DELETED')),
    retention_until DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    attributed_person_id UUID REFERENCES workspace_people(id),
    wallet_id UUID REFERENCES wallets(id),
    category_id UUID REFERENCES categories(id),
    voice_record_id UUID REFERENCES voice_records(id),
    transaction_type VARCHAR(30) NOT NULL
        CHECK (
            transaction_type IN (
                'INCOME',
                'EXPENSE',
                'TRANSFER',
                'LOAN_DISBURSEMENT',
                'LOAN_COLLECTION',
                'BORROWING_RECEIPT',
                'BORROWING_REPAYMENT',
                'ADJUSTMENT'
            )
        ),
    transaction_status VARCHAR(20) NOT NULL DEFAULT 'POSTED'
        CHECK (transaction_status IN ('DRAFT', 'PLANNED', 'POSTED', 'VOID')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    transaction_date DATE NOT NULL,
    transaction_time TIME,
    description VARCHAR(500),
    note TEXT,
    source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL'
        CHECK (
            source_type IN (
                'MANUAL',
                'QUICK_BUTTON',
                'QUICK_TEXT',
                'VOICE',
                'EXCEL_MIGRATION',
                'SYSTEM'
            )
        ),
    raw_input TEXT,
    source_reference VARCHAR(255),
    migration_key VARCHAR(64),
    wallet_unknown BOOLEAN NOT NULL DEFAULT FALSE,
    legacy_label VARCHAR(255),
    legacy_aggregate BOOLEAN NOT NULL DEFAULT FALSE,
    legacy_ambiguous BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (workspace_id, migration_key),
    CHECK (wallet_id IS NOT NULL OR wallet_unknown = TRUE OR transaction_status IN ('DRAFT', 'PLANNED'))
);

CREATE INDEX idx_transactions_workspace_date
    ON transactions (workspace_id, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_workspace_type
    ON transactions (workspace_id, transaction_type)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_category_date
    ON transactions (category_id, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE transfer_details (
    transaction_id UUID PRIMARY KEY REFERENCES transactions(id) ON DELETE CASCADE,
    source_wallet_id UUID NOT NULL REFERENCES wallets(id),
    destination_wallet_id UUID NOT NULL REFERENCES wallets(id),
    CHECK (source_wallet_id <> destination_wallet_id)
);

CREATE TABLE debts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    counterparty_person_id UUID NOT NULL REFERENCES workspace_people(id),
    direction VARCHAR(20) NOT NULL
        CHECK (direction IN ('RECEIVABLE', 'PAYABLE')),
    principal_amount NUMERIC(19,2) NOT NULL CHECK (principal_amount > 0),
    opened_on DATE NOT NULL,
    due_on DATE,
    closed_on DATE,
    debt_status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (debt_status IN ('OPEN', 'PARTIAL', 'PAID', 'CANCELLED')),
    note TEXT,
    origin_transaction_id UUID REFERENCES transactions(id),
    source_reference VARCHAR(255),
    migration_key VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, migration_key)
);

CREATE TABLE debt_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    debt_id UUID NOT NULL REFERENCES debts(id) ON DELETE CASCADE,
    transaction_id UUID REFERENCES transactions(id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    payment_date DATE NOT NULL,
    note TEXT,
    source_reference VARCHAR(255),
    migration_key VARCHAR(64) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE monthly_budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    jar_id UUID REFERENCES jars(id),
    category_id UUID REFERENCES categories(id),
    planned_amount NUMERIC(19,2) NOT NULL CHECK (planned_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((jar_id IS NOT NULL)::INTEGER + (category_id IS NOT NULL)::INTEGER = 1),
    UNIQUE (workspace_id, period_start, jar_id, category_id)
);

CREATE TABLE transaction_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    transaction_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    actor_user_id UUID REFERENCES users(id),
    action VARCHAR(20) NOT NULL
        CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'RESTORE', 'IMPORT')),
    before_data JSONB,
    after_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE migration_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    source_file_name VARCHAR(255) NOT NULL,
    source_file_sha256 VARCHAR(64) NOT NULL,
    migration_status VARCHAR(20) NOT NULL DEFAULT 'DRY_RUN'
        CHECK (migration_status IN ('DRY_RUN', 'RUNNING', 'COMPLETED', 'FAILED', 'ROLLED_BACK')),
    cutoff_date DATE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    summary JSONB,
    UNIQUE (workspace_id, source_file_sha256)
);

CREATE TABLE migration_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    migration_run_id UUID NOT NULL REFERENCES migration_runs(id) ON DELETE CASCADE,
    source_reference VARCHAR(255) NOT NULL,
    source_key VARCHAR(64) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    target_id UUID,
    item_status VARCHAR(20) NOT NULL
        CHECK (item_status IN ('READY', 'IMPORTED', 'NEEDS_REVIEW', 'SKIPPED', 'FAILED')),
    raw_data JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (migration_run_id, source_key)
);
