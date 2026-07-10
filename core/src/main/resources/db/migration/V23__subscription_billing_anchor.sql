-- Preserve the original billing day/month when a short month or non-leap year
-- temporarily clamps a subscription's next due date.
ALTER TABLE finance_subscription
    ADD COLUMN billing_anchor_month INTEGER,
    ADD COLUMN billing_anchor_day INTEGER;

UPDATE finance_subscription
SET billing_anchor_month = EXTRACT(MONTH FROM next_due_on)::INTEGER,
    billing_anchor_day = EXTRACT(DAY FROM next_due_on)::INTEGER;

ALTER TABLE finance_subscription
    ALTER COLUMN billing_anchor_month SET NOT NULL,
    ALTER COLUMN billing_anchor_day SET NOT NULL,
    ADD CONSTRAINT finance_subscription_anchor_month_check
        CHECK (billing_anchor_month BETWEEN 1 AND 12),
    ADD CONSTRAINT finance_subscription_anchor_day_check
        CHECK (billing_anchor_day BETWEEN 1 AND 31);
