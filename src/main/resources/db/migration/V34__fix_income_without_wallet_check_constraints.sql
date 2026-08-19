-- V34__fix_income_without_wallet_check_constraints.sql

-- 1. Safely find and drop the anonymous check constraint from V1 that references 'wallet_unknown'
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu
            ON tc.constraint_name = ccu.constraint_name
            AND tc.table_schema = ccu.table_schema
        WHERE tc.table_name = 'transactions'
          AND tc.constraint_type = 'CHECK'
          AND ccu.column_name = 'wallet_unknown'
    LOOP
        EXECUTE 'ALTER TABLE transactions DROP CONSTRAINT ' || quote_ident(r.constraint_name);
    END LOOP;
END $$;

-- 2. Re-create the constraint allowing NULL wallet_id for INCOME transactions
ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_wallet_required
        CHECK (
            wallet_id IS NOT NULL
            OR wallet_unknown = TRUE
            OR transaction_status IN ('DRAFT', 'PLANNED')
            OR transaction_type = 'INCOME'
        );

-- 3. Drop the public sources ledger constraint
ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS chk_transactions_public_sources_ledger;

-- 4. Re-create the public sources ledger constraint allowing affects_wallet_balance to be false for INCOME
ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_public_sources_ledger
        CHECK (
            transaction_type = 'INCOME'
            OR source_type NOT IN ('MANUAL', 'QUICK_BUTTON', 'QUICK_TEXT', 'VOICE')
            OR affects_wallet_balance = TRUE
        );
