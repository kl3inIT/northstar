-- Spring Modulith Event Publication Registry (the durable async outbox that
-- carries note-save events to the embedding indexer, and later api -> worker
-- jobs). Verbatim copy of the official DDL from spring-modulith-events-jdbc
-- 2.1.0 (schemas/v2/schema-postgresql.sql); schema-initialization stays off —
-- Flyway owns this table like every other.

CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
