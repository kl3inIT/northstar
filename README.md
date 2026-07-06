# Northstar

Personal growth OS + Obsidian-lite knowledge base with AI capture, study
(IELTS/HSK), scholarship tracking, calendar/planner, and an MCP interface for
coding agents. See [`docs/PRODUCT_PLAN.md`](./docs/PRODUCT_PLAN.md).

## Architecture

Modular monolith (Spring Modulith) with three deployables sharing one domain and
one PostgreSQL database:

```
core/                 one library: domain + Modulith modules (com.northstar.core.*)
apps/
  api/                REST + web  (runs Flyway migrations, exposes OpenAPI)
  mcp/                MCP server  (northstar_* tools for agents)
  worker/             @Scheduled + async event jobs (reminders, embeddings, compaction)
web/                  Vite + React + TS + Tailwind v4 + shadcn/ui (SPA)
mobile/               Flutter (later)
contracts/            openapi.json — web (TS) & mobile (Dart) generate typed clients
```

**Stack:** Spring Boot 4.1 · Java 25 · Gradle KTS · Spring Modulith 2.1 ·
PostgreSQL + pgvector · Flyway. Frontend: React 19 · TanStack Router/Query ·
shadcn/ui. Spring AI is deferred (keyword search first).

## Quickstart

```bash
# 1. database (Postgres + pgvector on :5432, Adminer on :8090)
docker compose up -d

# 2. backend
./gradlew build                 # compile + tests (incl. Modulith verification)
./gradlew :apps:api:bootRun     # http://localhost:8888

# 3. web
cd web && pnpm install && pnpm dev   # http://localhost:5173  (proxies /api -> :8888)
```

## Repo map

| Path | What |
|------|------|
| `core/` | domain + Modulith modules; Flyway migrations in `src/main/resources/db/migration` |
| `apps/api`, `apps/mcp`, `apps/worker` | the three Spring Boot apps |
| `build-logic/` | Gradle convention plugins (`northstar.*`) |
| `gradle/libs.versions.toml` | version catalog |
| `compose.yaml` | Postgres + pgvector + Adminer |
| `web/` | frontend SPA |
| `.github/` | Dependabot + CI |
| `docs/` | product plan, KB design, session continuity |
