<div align="center">
  <img src="./web/public/logo.png" alt="Northstar" width="96" />
  <h1>Northstar</h1>
  <p><strong>A personal growth operating system for knowledge, planning, finance, study, and AI-assisted daily work.</strong></p>
</div>

[![CI](https://github.com/kl3inIT/northstar/actions/workflows/ci.yml/badge.svg)](https://github.com/kl3inIT/northstar/actions/workflows/ci.yml) [![Mobile CI](https://github.com/kl3inIT/northstar/actions/workflows/mobile-ci.yml/badge.svg)](https://github.com/kl3inIT/northstar/actions/workflows/mobile-ci.yml) ![Java 25](https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white) ![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white) ![React 19](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=0B1220) ![Flutter](https://img.shields.io/badge/Flutter-3.44-02569B?logo=flutter&logoColor=white) ![PostgreSQL + pgvector](https://img.shields.io/badge/PostgreSQL%20%2B%20pgvector-4169E1?logo=postgresql&logoColor=white)

Northstar brings notes, projects, tasks, calendar, personal finance, study,
automations, and an AI assistant into one personal system. Raw input is captured
first, reviewed when it becomes structured data, and connected to a durable
Life -> Disciplines -> Projects spine instead of scattered across isolated apps.

The application is personal-first and actively developed. It runs as a web app,
a Cupertino-style Flutter client, a background worker, and an MCP server for
external agents.

## Product Areas

- **Assistant and Capture** - streamed tool-using chat, text/voice/receipt
  capture, editable drafts, attachments, citations, web research, and on-demand
  text to speech.
- **Knowledge** - Markdown notes, folders, tags, statuses, wiki links,
  backlinks, attachments, and hybrid keyword/vector search.
- **Planning** - disciplines, projects, milestones, tasks, recurring calendar
  events, free-slot lookup, and daily/weekly alignment reviews.
- **Finance** - VND ledger, balance reconciliation, learned categories,
  budgets, savings goals, subscriptions, CSV export, and spending insights.
- **Study** - study logs, Ebisu vocabulary memory, writing feedback, IELTS-style
  rubric guidance, speaking practice, and Azure pronunciation assessment.
- **Automations and Briefs** - typed persisted schedules, execution history,
  retries, public-source Morning Brief research, and reviewable note output.
- **AI routing** - runtime-configurable OpenAI, 9Router, OpenRouter, LiteLLM,
  and custom OpenAI-compatible gateway instances with capability-specific
  Chat, TTS, STT, image, embedding, web-search, and web-fetch catalogs.
- **Agent access** - streamable HTTP MCP tools for knowledge, tasks, calendar,
  projects, finance, study, and reviews.

## Architecture

Northstar is a modular monolith with three backend deployables sharing one
PostgreSQL database:

```text
core/                 domain library and Spring Modulith modules
apps/api/             REST API, web auth, Flyway owner, OpenAPI emitter
apps/mcp/             streamable HTTP MCP server
apps/worker/          scheduled indexing and automation worker
integrations/         AI, web research, and speech provider adapters
web/                  Vite + React + TypeScript SPA
mobile/               adaptive Cupertino-first Flutter client
contracts/            generated OpenAPI contract
build-logic/          Gradle convention plugins
```

Backend: Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring Security 7,
Spring AI, JPA, Flyway, PostgreSQL, and pgvector.

Frontend: React 19, TypeScript, Vite, Tailwind CSS v4, shadcn/ui, AI Elements,
TanStack Router, and TanStack Query. Mobile uses Flutter 3.44 and Dart 3.12.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for module boundaries, schema
ownership, authentication, provider routing, and deployment details.

## Quickstart

Requirements:

- Java 25
- Docker
- pnpm

```bash
# Local configuration. The example keeps login disabled for trusted local use.
cp .env.example .env
# Set OPENAI_API_KEY. Generate NORTHSTAR_AI_CREDENTIAL_KEY before saving
# runtime gateway credentials from Settings.

# PostgreSQL + pgvector
docker compose up -d

# API (the local profile imports .env)
SPRING_PROFILES_ACTIVE=local ./gradlew :apps:api:bootRun

# Web
pnpm -C web install
pnpm -C web dev
```

PowerShell API command:

```powershell
$env:SPRING_PROFILES_ACTIVE='local'
./gradlew :apps:api:bootRun
```

Local URLs:

- Web: `http://localhost:5173`
- API: `http://localhost:8888`
- MCP: `http://localhost:8081/mcp` when `apps/mcp` is running
- PostgreSQL: `localhost:5432`

To exercise the web login locally, set `NORTHSTAR_AUTH_ENABLED=true` plus
`NORTHSTAR_AUTH_USERNAME` and a bcrypt `NORTHSTAR_AUTH_PASSWORD_HASH` in
`.env`. Production Compose always activates the `prod` profile and uses the
server environment template under `docker/`.

## Verification

The repository treats compile, architecture, context, and UI checks as separate
gates. Common commands:

```bash
./gradlew --no-daemon compileJava
./gradlew :core:test
./gradlew --no-daemon clean test
pnpm -C web typecheck
pnpm -C web build

cd mobile
flutter analyze
flutter test
flutter build web --release
```

Use `bootRun` to run an application, not as a terminating verification gate.
The complete workflow lives in
[docs/guidelines/testing-harness.md](./docs/guidelines/testing-harness.md).

## Documentation

The repository is the system of record; chat history is not.

| Path | Purpose |
| --- | --- |
| [CLAUDE.md](./CLAUDE.md) / [AGENTS.md](./AGENTS.md) | Thin agent map and durable workflow rules |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Current stack, deployables, modules, and runtime behavior |
| [docs/vision.md](./docs/vision.md) | Product intent and future direction |
| [docs/roadmap.md](./docs/roadmap.md) | Delivered increments and backlog |
| [docs/specs/](./docs/specs/) | Current per-domain behavior |
| [docs/tests/](./docs/tests/) | Coverage matrix and known verification gaps |
| [docs/decisions/](./docs/decisions/) | Append-only architectural rationale |
| [docs/increments/](./docs/increments/) | Active and completed design/plan history |

Meaningful increments follow design -> plan -> implementation -> verification ->
consolidation. Generated API clients are never edited by hand.

## License

No open-source license has been selected. The source is currently published for
reference; no redistribution or derivative-use rights are granted by default.
