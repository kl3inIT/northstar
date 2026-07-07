# 0003 - One Domain, Three Deployables

Status: accepted

## Context

Northstar needs REST/web delivery, MCP access for agents, and background
processing for heavy jobs. Splitting business logic across services would
duplicate behavior and slow local development, but one process would mix
interactive request handling with long-running work.

## Decision

Keep one domain library in `core` and expose it through three deployables:

- `apps/api` for REST, web delivery, Flyway, and OpenAPI.
- `apps/mcp` for MCP tools.
- `apps/worker` for scheduled background work.

All three use the same PostgreSQL database. Business logic belongs in `core`
first, then gets exposed through the appropriate delivery app.

## Consequences

Feature behavior has one home. Module boundaries are verified through Spring
Modulith. The API owns migrations; other deployables validate the existing
schema.
