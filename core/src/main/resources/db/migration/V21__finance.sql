-- Finance: a single ledger table, capture-first. Every entry starts as natural
-- language (text/voice/receipt photo) parsed by the LLM; this table stores the
-- confirmed result. Deliberately absent: wallets/accounts, budgets, transfers,
-- multi-currency — see docs/decisions. Amounts are VND (no decimals) as BIGINT.
-- `exceptional` marks one-off atypical purchases (vs routine spending): the
-- weekly review aggregates them separately — the one feedback structure with
-- experimental evidence of changing behavior.
CREATE TABLE finance_transaction (
    id          UUID         PRIMARY KEY,
    type        VARCHAR(16)  NOT NULL,
    amount      BIGINT       NOT NULL,
    occurred_on DATE         NOT NULL,
    description VARCHAR(255) NOT NULL,
    category    VARCHAR(64)  NOT NULL,
    exceptional BOOLEAN      NOT NULL DEFAULT FALSE,
    source      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT finance_transaction_type_check CHECK (type IN ('EXPENSE', 'INCOME')),
    CONSTRAINT finance_transaction_amount_check CHECK (amount > 0),
    CONSTRAINT finance_transaction_source_check CHECK (source IN ('CAPTURE', 'ASSISTANT', 'MANUAL'))
);

-- Every read path is month-scoped (page, summary, review).
CREATE INDEX finance_transaction_occurred_idx ON finance_transaction (occurred_on);
