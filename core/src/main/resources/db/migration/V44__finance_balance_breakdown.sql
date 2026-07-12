-- Aggregate check-ins now require an explicit balance breakdown. Historical
-- anchors cannot be classified reliably, so reset only reconciliation data.
UPDATE finance_balance_check_in SET adjustment_transaction_id = NULL;
DELETE FROM finance_transaction WHERE source = 'RECONCILIATION';
DELETE FROM finance_balance_check_in;

ALTER TABLE finance_balance_check_in DROP COLUMN actual_balance;
ALTER TABLE finance_balance_check_in ADD COLUMN bank_balance BIGINT NOT NULL;
ALTER TABLE finance_balance_check_in ADD COLUMN cash_balance BIGINT NOT NULL;
ALTER TABLE finance_balance_check_in ADD COLUMN e_wallet_balance BIGINT NOT NULL;
ALTER TABLE finance_balance_check_in ADD COLUMN other_balance BIGINT NOT NULL;

ALTER TABLE finance_balance_check_in
    ADD CONSTRAINT finance_balance_bank_check CHECK (bank_balance >= 0),
    ADD CONSTRAINT finance_balance_cash_check CHECK (cash_balance >= 0),
    ADD CONSTRAINT finance_balance_e_wallet_check CHECK (e_wallet_balance >= 0),
    ADD CONSTRAINT finance_balance_other_check CHECK (other_balance >= 0);
