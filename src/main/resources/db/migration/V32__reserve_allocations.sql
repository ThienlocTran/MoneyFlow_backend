CREATE TABLE reserve_allocations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    purpose_type VARCHAR(30) NOT NULL DEFAULT 'CUSTOM',
    wallet_id UUID NULL,
    category_id UUID NULL,
    jar_id UUID NULL,
    target_date DATE NULL,
    note TEXT NULL,
    released_at TIMESTAMP NULL,
    cancelled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reserve_allocations_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id),
    CONSTRAINT fk_reserve_allocations_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_reserve_allocations_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_reserve_allocations_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_reserve_allocations_jar FOREIGN KEY (jar_id) REFERENCES jars(id),
    CONSTRAINT chk_reserve_allocations_amount CHECK (amount > 0),
    CONSTRAINT chk_reserve_allocations_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CANCELLED')),
    CONSTRAINT chk_reserve_allocations_purpose CHECK (purpose_type IN ('EMERGENCY_FUND', 'RENT', 'BILL', 'GOAL', 'CATEGORY_JAR', 'DEBT_PAYMENT', 'CUSTOM'))
);

CREATE INDEX idx_reserve_allocations_workspace_status
ON reserve_allocations (workspace_id, status);

CREATE INDEX idx_reserve_allocations_workspace_target
ON reserve_allocations (workspace_id, target_date, status);
