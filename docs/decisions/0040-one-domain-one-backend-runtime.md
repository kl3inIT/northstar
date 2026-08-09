# 0040 - One Domain, One Backend Runtime

Status: accepted

## Context

Northstar is a single-user modular monolith. The former API, MCP and worker
deployables shared one domain, schema and release cadence, but paid for three
Spring contexts, three JVMs and three datasource pools. That operational cost no
longer buys useful scale or failure isolation.

## Decision

Compose REST/web delivery, MCP streamable HTTP and scheduled jobs into one
`northstar-server` Spring Boot process on port 8888. Keep their source boundaries:

- `apps/api` is the composition root and REST delivery module.
- `apps/mcp` is a Spring library that owns MCP transport guards.
- `apps/worker` is a Spring library that owns indexing and durable jobs.
- `core` remains the domain and migration library.

The process owns exactly one datasource, Flyway lifecycle and health surface.
MCP stays at `/mcp`, uses a dedicated shared-token header rather than browser
sessions, and retains its rate/body guards. db-scheduler keeps its dedicated
executor; Spring scheduled work never runs on HTTP request threads. Docker grants
the sequential scheduler shutdown phases a five-minute termination envelope.

## Consequences

Northstar publishes and deploys one backend image instead of three. Resource
budgets, startup, rollback and observability become simpler. API, MCP and job
failures now share a process boundary, which is acceptable for the single-user
scope. Idempotent indexing, durable scheduler locks and explicit singleton
integration tests protect against duplicate background registration.

Production deployment is manual. The first cutover can roll back to the legacy
three-container topology using the pre-deploy image and Compose snapshot.