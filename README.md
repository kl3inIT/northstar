# Northstar

[![CI](https://github.com/kl3inIT/northstar/actions/workflows/ci.yml/badge.svg)](https://github.com/kl3inIT/northstar/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![React 19](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=0B1220)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white)
![PostgreSQL + pgvector](https://img.shields.io/badge/PostgreSQL%20%2B%20pgvector-4169E1?logo=postgresql&logoColor=white)
![MCP](https://img.shields.io/badge/MCP-enabled-111827)
![License](https://img.shields.io/badge/license-pending-lightgrey)

Northstar is a personal growth OS: an Obsidian-lite knowledge base plus AI
capture, tasks, projects, disciplines, calendar planning, search, and MCP tools
for coding agents.

It is built around a simple premise: productivity should not become another job.
Captured thoughts should find their place in a life structure instead of forcing
the user to constantly manage folders, tags, templates, and dashboards.

## Status

Northstar is an early personal project and reference repo. The code is usable as
a working prototype, but the product model, APIs, and UI are still moving.

Before publishing as a formal open-source project, choose a license and add a
`LICENSE` file.

## Core Model: LDP

Northstar uses an LDP spine: **Life -> Disciplines -> Projects**.

- **Life** creates meaning: the direction a person is trying to move toward.
- **Disciplines** create direction: durable areas such as study, career, health,
  finance, personal brand, or scholarship applications.
- **Projects** create action: concrete multi-step outcomes inside a discipline.
- **Tasks** are the next actions.
- **Calendar events** reserve time.
- **Notes** preserve durable context, decisions, and learning.

Example: "work on my scholarship essay today" is not just a task. It belongs to
a project, that project serves a discipline, and that discipline should connect
back to the kind of life the user wants to build.

## What Is Built

- Markdown knowledge base with folders, tags, note status, wiki links, and
  backlinks.
- Single-user web login with Spring Security session auth and SPA CSRF.
- AI capture flow for turning raw thoughts into structured note drafts.
- Disciplines, projects, milestones, tasks, and calendar planning.
- Recurring calendar events and free-slot lookup.
- Attachment/search pipeline with PostgreSQL, pgvector, and derived indexes.
- In-app assistant tools and streamable-http MCP tools for external agents.
- React web app backed by a Java/Spring Boot modular monolith.
- Repository-as-system-of-record harness for AI-assisted development.

## Architecture Snapshot

Northstar is one domain with three backend deployables sharing PostgreSQL:

```text
core/                 domain library and Spring Modulith modules
apps/api/             REST API, Flyway owner, OpenAPI emitter
apps/mcp/             streamable-http MCP tools for agents
apps/worker/          scheduled background indexing worker
web/                  Vite React SPA
contracts/            generated OpenAPI contract
build-logic/          Gradle convention plugins
```

Stack:

- Backend: Spring Boot 4.1, Java 25, Gradle Kotlin DSL, Spring Modulith 2.1,
  Spring Security 7, Spring Data JPA, Flyway, PostgreSQL, pgvector, Spring AI.
- Frontend: Vite, React 19, TypeScript, Tailwind v4, shadcn/ui, TanStack Router,
  TanStack Query.
- Contract: OpenAPI emitted by the API and consumed by generated clients.

## Quickstart

Requirements:

- Java 25
- Docker
- pnpm

Run locally:

```bash
# 0. local config
cp .env.example .env
# fill OPENAI_API_KEY, NORTHSTAR_AUTH_USERNAME, and NORTHSTAR_AUTH_PASSWORD_HASH

# 1. database
docker compose up -d

# 2. backend API
./gradlew :apps:api:bootRun

# 3. web app
pnpm -C web install
pnpm -C web dev
```

Local URLs:

- Web: `http://localhost:5173`
- API: `http://localhost:8888`
- MCP: `http://localhost:8081/mcp` when `apps/mcp` is running
- Adminer: `http://localhost:8090`

## Documentation

The repository is the system of record. Start here, then follow the focused docs
instead of relying on chat history.

| Path | Purpose |
| --- | --- |
| [harness.md](./harness.md) | Repository operating model used for AI-assisted development |
| [CLAUDE.md](./CLAUDE.md) / [AGENTS.md](./AGENTS.md) | Thin agent map and workflow rules |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Current stack, deployables, module layout, schema ownership, commands |
| [docs/vision.md](./docs/vision.md) | Product intent and future scope |
| [docs/roadmap.md](./docs/roadmap.md) | Increment status and backlog |
| [docs/conventions.md](./docs/conventions.md) | Code, schema, generated client, and commit conventions |
| [docs/guidelines/](./docs/guidelines/) | Testing harness, agent safety, asset pipeline, usage guidance |
| [docs/specs/](./docs/specs/) | Current per-domain behavior |
| [docs/tests/](./docs/tests/) | Per-domain test coverage and gaps |
| [docs/decisions/](./docs/decisions/) | Append-only rationale for significant choices |
| [docs/increments/](./docs/increments/) | Active and completed increment design/plan history |

Old monolithic docs are intentionally retired. Current state is
`ARCHITECTURE.md` plus the durable specs, tests, guidelines, and decisions.

## Development Harness

Northstar is built with a lightweight production vibe-coding harness:

- Repo docs are the system of record, not chat history.
- `CLAUDE.md` is a thin map; current facts live in `ARCHITECTURE.md`, specs,
  tests, guidelines, and decisions.
- Every meaningful increment goes design -> plan -> execute -> consolidate.
- Java/Spring feedback is treated as part of the creative loop: prompt, compile,
  test, refactor, document, repeat.
- The harness intentionally keeps friction: compiler, types, Modulith
  boundaries, Flyway validation, generated contracts, and browser checks all
  push back before a change is considered done.

## Verification

Use the full testing harness in
[docs/guidelines/testing-harness.md](./docs/guidelines/testing-harness.md).

Common commands:

```bash
./gradlew --no-daemon compileJava
./gradlew :core:test
./gradlew --no-daemon clean test
pnpm -C web typecheck
pnpm -C web build
```

Use `bootRun` only to run an app, not as a terminating verification gate.

## Contributing

This repo is still personal-first. If you explore or fork it:

- Keep business logic in `core` first.
- Keep the docs aligned with the operating model in `harness.md`.
- Do not edit generated clients by hand.
- Add or update specs/tests/decisions when durable behavior changes.

See [docs/conventions.md](./docs/conventions.md) for local conventions.
