-- Finance V1.5 accuracy layer: aggregate end-of-day balance anchors and the
-- user's recent category corrections. Analytics and recurring suggestions are
-- derived from the existing ledger and need no copied aggregate tables.
ALTER TABLE finance_transaction DROP CONSTRAINT finance_transaction_source_check;
ALTER TABLE finance_transaction ADD CONSTRAINT finance_transaction_source_check
    CHECK (source IN ('CAPTURE', 'ASSISTANT', 'MANUAL', 'SUBSCRIPTION', 'RECONCILIATION'));

CREATE TABLE finance_balance_check_in (
    id                        UUID        PRIMARY KEY,
    checked_on                DATE        NOT NULL,
    actual_balance            BIGINT      NOT NULL,
    expected_balance          BIGINT      NOT NULL,
    discrepancy               BIGINT      NOT NULL,
    adjustment_transaction_id UUID,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT finance_balance_actual_check CHECK (actual_balance >= 0),
    CONSTRAINT finance_balance_adjustment_fk FOREIGN KEY (adjustment_transaction_id)
        REFERENCES finance_transaction (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX finance_balance_check_in_date_uidx
    ON finance_balance_check_in (checked_on);

CREATE TABLE finance_category_correction (
    id              UUID         PRIMARY KEY,
    transaction_type VARCHAR(16) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    description_key VARCHAR(255) NOT NULL,
    category        VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT finance_category_correction_type_check
        CHECK (transaction_type IN ('EXPENSE', 'INCOME'))
);

CREATE UNIQUE INDEX finance_category_correction_description_uidx
    ON finance_category_correction (transaction_type, description_key);
