-- Subscriptions post their own expenses: a worker job writes the ledger row on
-- the due date and advances the cycle — mark-paid stays only as a manual
-- override. SUBSCRIPTION joins the source vocabulary so auto-posted rows are
-- distinguishable from human entries. A subscription can carry an optional
-- cancel-reminder date ("nhắc tôi hủy trước ngày X" — a trial about to convert,
-- a plan to drop the service at year end); the worker creates ONE reminder task
-- for it (tracked by cancel_reminder_task_id so it never duplicates).
ALTER TABLE finance_transaction DROP CONSTRAINT finance_transaction_source_check;
ALTER TABLE finance_transaction ADD CONSTRAINT finance_transaction_source_check
    CHECK (source IN ('CAPTURE', 'ASSISTANT', 'MANUAL', 'SUBSCRIPTION'));

ALTER TABLE finance_subscription ADD COLUMN cancel_reminder_on DATE;
ALTER TABLE finance_subscription ADD COLUMN cancel_reminder_task_id UUID;
