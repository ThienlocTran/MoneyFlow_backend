ALTER TABLE transactions
    ADD COLUMN is_historical BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE transactions
    ADD COLUMN affects_wallet_balance BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE transactions
SET is_historical = TRUE,
    affects_wallet_balance = FALSE
WHERE source_type = 'EXCEL_MIGRATION'
  AND wallet_unknown = TRUE;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_balance_effect_source
        CHECK (affects_wallet_balance = TRUE OR source_type IN ('EXCEL_MIGRATION', 'SYSTEM'));

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_public_sources_ledger
        CHECK (source_type NOT IN ('MANUAL', 'QUICK_BUTTON', 'QUICK_TEXT', 'VOICE') OR affects_wallet_balance = TRUE);

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_transfer_ledger
        CHECK (transaction_type <> 'TRANSFER' OR affects_wallet_balance = TRUE);
