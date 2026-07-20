ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_adjustment_direction_check;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_adjustment_direction_check
        CHECK (
            (transaction_type = 'ADJUSTMENT'
                AND adjustment_direction IS NOT NULL
                AND adjustment_direction IN ('INCREASE', 'DECREASE'))
            OR (transaction_type <> 'ADJUSTMENT' AND adjustment_direction IS NULL)
        ) NOT VALID;
