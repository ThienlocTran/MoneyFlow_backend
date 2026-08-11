ALTER TABLE planned_obligations
ADD COLUMN paid_at TIMESTAMP NULL;

ALTER TABLE planned_obligations
ADD COLUMN paid_note TEXT NULL;

CREATE UNIQUE INDEX ux_planned_obligations_linked_transaction
ON planned_obligations (linked_transaction_id);

CREATE INDEX idx_planned_obligations_workspace_linked_transaction
ON planned_obligations (workspace_id, linked_transaction_id);
