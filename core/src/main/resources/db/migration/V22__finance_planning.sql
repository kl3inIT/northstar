-- Finance planning extends the V21 capture-first ledger with the three pieces
-- the user actively maintains: monthly category limits, savings progress, and
-- recurring subscription charges. None of these introduce accounts/transfers.
CREATE TABLE finance_budget (
    id           UUID         PRIMARY KEY,
    month_start  DATE         NOT NULL,
    category     VARCHAR(64)  NOT NULL,
    limit_amount BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT finance_budget_month_start_check
        CHECK (month_start = date_trunc('month', month_start)::date),
    CONSTRAINT finance_budget_limit_check CHECK (limit_amount > 0)
);

CREATE UNIQUE INDEX finance_budget_month_category_uidx
    ON finance_budget (month_start, lower(category));

CREATE TABLE finance_savings_goal (
    id                   UUID          PRIMARY KEY,
    name                 VARCHAR(120)  NOT NULL,
    target_amount        BIGINT        NOT NULL,
    saved_amount         BIGINT        NOT NULL DEFAULT 0,
    target_date          DATE,
    monthly_contribution BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT finance_goal_target_check CHECK (target_amount > 0),
    CONSTRAINT finance_goal_saved_check CHECK (saved_amount >= 0),
    CONSTRAINT finance_goal_monthly_check CHECK (monthly_contribution >= 0)
);

CREATE TABLE finance_subscription (
    id          UUID          PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    amount      BIGINT        NOT NULL,
    category    VARCHAR(64)   NOT NULL,
    cycle       VARCHAR(16)   NOT NULL,
    next_due_on DATE          NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT finance_subscription_amount_check CHECK (amount > 0),
    CONSTRAINT finance_subscription_cycle_check CHECK (cycle IN ('MONTHLY', 'YEARLY'))
);

CREATE INDEX finance_subscription_due_idx
    ON finance_subscription (active, next_due_on);
