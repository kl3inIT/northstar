# 0034 — Assistant turns use durable idempotency claims

Status: accepted

## Context

The web composer can receive repeated Enter and click events before AI SDK chat
state becomes busy. Network retries can also repeat a request after a model or
tool has started. A database-backed assistant tool may commit its side effect
before the stream or client connection reports failure, so transport success is
not a reliable signal that a turn is safe to run again.

Older web and mobile clients already call the chat endpoint without an
idempotency header and must remain compatible during rollout.

## Decision

The web composer holds a synchronous single-flight lock from attachment upload
through chat submission and sends one `Idempotency-Key` per user action. The API
atomically claims that key within its conversation before route resolution,
model invocation, or tool execution. A repeated supplied key returns `409`.

Claims are not released after route, model, tool, stream, or client failures.
This preserves at-most-once execution when a side effect may already have
committed. An explicit user retry creates a new message with a new key. Deleting
a conversation deletes its claims. A missing header remains supported by
generating a fresh server key, preserving the behavior of older clients.

## Consequences

- Duplicate UI events and transport retries cannot run the same claimed action
  twice.
- A failed turn cannot be resumed with the same key; the user retries it as a
  new action after checking the visible outcome.
- The composer preserves its draft when submission is rejected so that explicit
  retry does not require retyping.
- Exactly-once delivery is not claimed; the boundary provides at-most-once
  execution for a supplied key.
