# Northstar Architecture

This is the source of truth for how the product is currently built. Planned
architecture that has not landed in code belongs in `docs/vision.md` or the
active increment, not here.

## Stack

- Backend: Spring Boot 4.1, Java 25, Gradle Kotlin DSL, Spring Modulith 2.1,
  Spring Security 7, Spring Data JPA, PostgreSQL, pgvector, Flyway, Spring AI.
- Frontend: Vite, React 19, TypeScript, Tailwind v4, shadcn/ui, TanStack Router,
  TanStack Query.
- Contract: the API emits `contracts/openapi.json`; Hey API generates the web
  fetch client, SDK functions, and DTO types from that contract.
- Build config: dependency versions live in `gradle/libs.versions.toml`; shared
  Gradle convention plugins live in `build-logic/`.

## Repository Layout

```text
core/                 domain library and Spring Modulith modules
apps/api/             REST app, web delivery, Flyway owner, OpenAPI emitter
apps/mcp/             streamable-http MCP server for external agents
apps/worker/          headless scheduled worker for heavy background indexing
web/                  Vite React SPA
contracts/            generated OpenAPI contract
build-logic/          Gradle convention plugins
```

## Deployables

Northstar is one domain with three backend deployables:

- `core` owns business logic, entities, repositories, services, migrations, and
  module boundaries under `com.northstar.core`.
- `apps/api` exposes REST endpoints, runs Flyway migrations, serves actuator
  endpoints, owns web session authentication, wires Spring AI/OpenAI for
  interactive AI features, emits OpenAPI, and talks to the same PostgreSQL
  database as the other apps.
- `apps/mcp` exposes MCP tools over streamable HTTP at `/mcp`. It scans
  `com.northstar.core`, reads the already-migrated schema, and does not run
  Flyway in production.
- `apps/worker` is a non-web process with scheduling enabled. It owns heavy
  indexing work such as embeddings and image captions, uses the same schema, and
  does not run Flyway in production.

The app classes are explicitly named `NorthstarApiApplication`,
`NorthstarMcpApplication`, and `NorthstarWorkerApplication`. The package root is
`com.northstar`.

## Domain Modules

Business logic lives in `core/src/main/java/com/northstar/core/*` as Spring
Modulith modules. Current modules include:

- `discipline` - methodology spine for life goals and disciplines.
- `project` - staged projects under disciplines with derived milestone progress.
- `task` - todo store with due date/time, status, and project/discipline links.
- `calendar` - calendar events, recurrence, cancelled occurrences, and free-slot
  discovery.
- `note` - Markdown knowledge base with wiki links and backlinks.
- `capture` - AI capture draft generation for notes and future entity parsing.
- `search` - keyword/vector search and attachment text/image indexing support.
- `attachment` - stored uploaded content and metadata.
- `assistant` - tool definitions shared by the in-app assistant and MCP.
- `alignment`, `finance`, `habit`, `scholarship`, `study`, `shared` - current
  or reserved domain modules.

Cross-module coupling must go through public APIs or events. Modulith
verification in `:core:test` is the boundary check.

## Data And Schema

- PostgreSQL is the source of truth.
- Flyway migrations live in `core/src/main/resources/db/migration` and travel on
  the `:core` classpath.
- `apps/api` runs Flyway at startup. `apps/mcp` and `apps/worker` have Flyway
  disabled in production and validate the already-migrated schema.
- JPA uses `ddl-auto: validate`; every mapped entity/column must match a Flyway
  migration.
- Note bodies are Markdown in the database. Wiki links/backlinks and vector
  search data are derived from that source data.
- pgvector schema is owned by Flyway; Spring AI vectorstore initialization is
  disabled.

## AI And Search

- `:core` depends on Spring AI APIs but stays provider-agnostic where possible.
- `apps/api` wires ChatClient, OpenAI chat, chat memory JDBC, tool search, and
  pgvector support for interactive capture, assistant, alignment, and query
  embedding.
- Assistant text history uses Spring AI's `spring_ai_chat_memory`; assistant
  tool workflow replay uses the Northstar-owned
  `northstar_assistant_tool_trace` projection table.
- `apps/worker` wires OpenAI and pgvector for indexing jobs that should not
  compete with API request threads.
- Search combines durable PostgreSQL data with derived keyword/vector indexes.
  Derived indexes are disposable and can be rebuilt from source records.

## Client Contract

- Runtime communication is HTTP and JSON.
- `apps/api` emits OpenAPI through springdoc.
- `web` generates Hey API TypeScript DTOs, a Fetch API client, and SDK
  functions from `contracts/openapi.json` into `web/src/lib/hey-api/`.
- The generated fetch client is configured through `web/src/lib/hey-api-config.ts`,
  which calls Hey's `client.setConfig()` to route requests through the app's
  CSRF/session-aware `apiFetch` transport.
- Normal JSON CRUD endpoints use the generated SDK functions. AI SDK chat
  streaming still calls `apiFetch` directly because it speaks the AI SDK UI
  message stream rather than a plain JSON response.
- Do not hand-write generated client types. Change the API, regenerate the
  contract, then regenerate the client.

## Authentication

- The web app uses Spring Security 7 servlet security with a server-side HTTP
  session and a single configured user.
- Credentials are supplied through `NORTHSTAR_AUTH_USERNAME` and
  `NORTHSTAR_AUTH_PASSWORD_HASH`; plaintext passwords are not stored in the
  repository.
- State-changing same-origin web requests use Spring Security SPA CSRF tokens
  (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` request header).
- `GET /api/auth/me`, `GET /api/auth/csrf`, `POST /api/auth/login`, and
  `POST /api/auth/logout` are the current auth endpoints.
- Mobile authentication is intentionally not implemented yet. A future Flutter
  client should use a separate token flow instead of browser local storage.

## Commands

```bash
docker compose up -d
./gradlew build
./gradlew --no-daemon compileJava
./gradlew :core:test
./gradlew :apps:api:bootRun

pnpm -C web install
pnpm -C web dev
pnpm -C web typecheck
pnpm -C web build
pnpm -C web gen:api
```

Use `./gradlew --no-daemon clean test` for the context-load gate. Do not use
`bootRun` as a terminating verification command.
