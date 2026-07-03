-- Optimistic locking for the two mutable aggregates: @Version bumps on every
-- UPDATE, and a stale write (editor tab racing another writer) surfaces as an
-- OptimisticLockingFailureException -> HTTP 409 instead of silently losing data.
ALTER TABLE note ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE task ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
