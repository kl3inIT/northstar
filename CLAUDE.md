# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Northstar is a personal-growth OS + Obsidian-lite knowledge base. Product intent and
decisions live in [`AGENTS.md`](./AGENTS.md), [`PROJECT_CONTEXT.md`](./PROJECT_CONTEXT.md),
and [`docs/PRODUCT_PLAN.md`](./docs/PRODUCT_PLAN.md) — read those for *what* to build.
This file is *how* to work in the repo: stack, commands, architecture, and the gates.

## Stack

Spring Boot 4.1 · Java 25 · Gradle (Kotlin DSL) · Spring Modulith 2.1 · Spring Data
JPA · PostgreSQL + pgvector · Flyway. Frontend: Vite · React 19 · TypeScript ·
Tailwind v4 · shadcn/ui · TanStack Router + Query. Client↔server contract: OpenAPI.
Spring AI is not wired yet — keyword search (Postgres `tsvector`) comes first;
embeddings/MCP tools are added later.

## Commands

```bash
docker compose up -d                       # Postgres + pgvector :5432, Adminer :8090
./gradlew build                            # compile + all tests (incl. Modulith verify)
./gradlew --no-daemon compileJava          # fast Java-only ground truth
./gradlew :core:test                       # ApplicationModules.verify() — module boundaries
./gradlew :core:test --tests "com.northstar.core.note.NoteRepositoryTests"   # a single test
./gradlew :apps:api:bootRun                # run api on :8888 (auto-starts compose in dev)

pnpm -C web install
pnpm -C web dev                            # web on :5173, proxies /api -> :8888
pnpm -C web typecheck                      # tsc --noEmit (esbuild strips types, does NOT check them)
pnpm -C web build                          # tsc + vite build
pnpm -C web gen:api                        # regenerate the typed API client from contracts/openapi.json
```

## Architecture (the big picture)

**One domain, three deployables.** All business logic lives in the `core/` library
(`com.northstar.core.*`) as Spring Modulith modules — `note`, `capture`, `task`,
`study`, `scholarship`, `discipline`, `finance`, `habit`, `calendar`, `search`,
`shared`. The three apps in `apps/` (`api`, `mcp`, `worker`) are **thin bootstraps**:
each is just a `main()` + a delivery adapter (REST controllers / MCP tools /
`@Scheduled` + event listeners) that depends on `:core`, scans `com.northstar.core`,
and talks to the **same** Postgres. So a feature is built in a `core` module first,
then exposed through whichever app(s) deliver it — never duplicated across apps.

**Modules talk through public APIs and events, not internals.** Cross-module coupling
is a Modulith violation caught by `:core:test`. Heavy/async work (embeddings, memory
compaction, reminders, entity extraction) flows as Modulith domain events that the
`worker` consumes via the Event Publication Registry (a durable async outbox) — that
is why worker is a separate process: LLM-latency jobs must not block api request
threads.

**Contract-driven clients.** The api emits `contracts/openapi.json` (springdoc); `web`
(TypeScript) and, later, `mobile` (Flutter/Dart) generate typed clients from it. The
runtime is plain HTTP+JSON — OpenAPI only keeps the types in sync. Never hand-write
client types; regenerate.

**Schema ownership.** Flyway migrations live in `core/src/main/resources/db/migration`
(on the classpath, so they travel with the domain). The `api` runs them at startup;
`mcp` and `worker` have Flyway disabled and read the already-migrated schema. JPA uses
`ddl-auto: validate`, so every `@Entity`/`@Column` MUST match a migration column.

**The methodology spine.** `life_goals` and `disciplines` (the LDP Life→Disciplines→
Projects model, see `docs/PRODUCT_PLAN.md` §0.16) exist from V1; other modules FK to a
discipline. This is what makes Study↔Scholarship (requirement-gap) one product, not
two apps.

Build config: version catalog in `gradle/libs.versions.toml`; shared Gradle config in
`build-logic/` convention plugins (`northstar.java-conventions`,
`northstar.spring-library-conventions`, `northstar.spring-boot-app-conventions`).

## Gates before a task is done

Code compiling is not done. Three gates, in order; never claim a gate passed without
showing evidence. Use the MCP tool when connected (primary); else the fallback.

| Gate | Primary (MCP, if connected) | Fallback (always) |
|---|---|---|
| **1 · API & static** | verify EVERY unfamiliar symbol against **the jar/sources in the Gradle cache** (Context7 only for usage patterns) before typing it, AND run the JetBrains inspection (`get_file_problems`) per file | `./gradlew compileJava` + `pnpm -C web typecheck` + `./gradlew :core:test` + the mechanical checks in `northstar-static-analysis` |
| **2 · Context loads** | *(no MCP substitute)* | `./gradlew --no-daemon clean test` — boots the Spring context, runs Flyway + Testcontainers tests, then EXITS |
| **3 · Runs / renders** | drive the running app with **Playwright** (SPA) | `/run` and `/verify` skills; or `bootRun` in the background, poll `http://localhost:8888/actuator/health` until UP, drive, then shut down |

**Never use `bootRun` (or any non-terminating server start) as the Gate-2 check** — it
does not exit and hangs your turn. Gate 2 is `clean test`.

`compileJava`/`tsc` are BLIND to config and wiring: a JPA field with no column, a
duplicated `application.yml` key, a Modulith boundary breach, a stale generated OpenAPI
client — all compile clean and fail at boot/render. A green `clean test` is necessary
but NOT sufficient: context-load tests boot the context but do NOT open new views or
exercise new flows — that is Gate 3.

**Evidence in your completion report.** Per file you touched: its static-check verdict.
Per behavior you added: how you verified it (test name, or render walk). "BUILD
SUCCESSFUL, done" with no per-file check and no run evidence is a non-answer.

## Skill routing

Read the most specific skill before you act:

- **Verify an unfamiliar Spring/Modulith/JPA/npm symbol before typing it** → `northstar-verify-api-symbol`
- **Gate-1 static checks on files you wrote** (JetBrains inspection / compile / Modulith verify / mechanical) → `northstar-static-analysis`
- **Write or change a test** (backend Testcontainers/Modulith, frontend Vitest/RTL/MSW/Playwright, Spring AI) → `northstar-create-test`
- **Gate 3 — drive the real app** → built-in `/run` and `/verify`

## Assets (images / SVG / icons)

Do NOT hand-author image assets — favicons, logos, illustrations, OG images, or
decorative SVGs. Hand-drawn SVG looks amateur and is hard to iterate. Route every
asset through the image-generation pipeline:

- **In Codex** → use the `image` skill directly (it drives Gemini / Flux / Ideogram
  and web-optimizes the output).
- **In Claude Code** → you have no image model, so use the `orchestration` skill to
  dispatch the asset task to a Codex worker (`orca` must be running); the worker runs
  the `image` skill and writes the file back. Wait for `worker_done`, then verify the
  file rendered.

Exception: tiny, purely-structural markup a human would also type by hand (e.g. a
one-line `<svg>` wrapper you are wiring into a component) is fine. Anything a designer
would make — generate it, don't draw it.

## Anti-hallucination

This stack rides recent majors (Boot 4, Modulith 2, Testcontainers 2, Tailwind 4,
React 19) where Boot-3 / Tailwind-3 muscle memory is actively wrong. Before typing any
symbol not already in this repo's `src/`, verify it (the jar/sources in the Gradle
cache or `node_modules` `.d.ts` are primary; grep a call site; Context7 only for usage
patterns/snippets) — and verify it is the **current, non-deprecated** name,
not a shim: a deprecated symbol compiles (with a warning) and hides that it was renamed
(e.g. `org.testcontainers.containers.PostgreSQLContainer` → `org.testcontainers.postgresql.PostgreSQLContainer`;
`@MockBean` → `@MockitoBean`). A deprecation warning is a blocker to fix now. Catalogued
traps are in `northstar-verify-api-symbol`.

## When something fails — it is almost never "pre-existing"

If the suite was green and goes red after your change, assume you broke it. Real
failure modes on this stack:

- **`cannot find symbol` / `package ... does not exist` on a Spring annotation** → it
  moved in Boot 4. E.g. `@EntityScan` is now `org.springframework.boot.persistence.autoconfigure`,
  and the web starter is `spring-boot-starter-webmvc`. Verify in the jar, do not guess.
- **`ApplicationModules.of(...)` throws `IllegalArgumentException`** → the type must be
  `@Modulithic` / `@Modulith` / `@SpringBootApplication` (see `NorthstarModules`).
- **`:core:test` fails (verify())** → a `core` module reached into another module's
  internals; route through its public API or a Modulith event instead.
- **`Not a managed type: ...Entity` at boot** → the app did not scan `com.northstar.core`
  (check `scanBasePackages` / `@EntityScan` / `@EnableJpaRepositories` on the app class).
- **Schema validation fails at startup** (`ddl-auto: validate`) → a mapped column has no
  matching DDL; add/fix a Flyway migration in `core/src/main/resources/db/migration`.
- **Config silently lost / boot misconfigured** → a duplicated top-level key in an
  `application.yml` (e.g. `spring:` declared twice). YAML keeps the last one.

Fix the cause and re-run until green. A red gate you cannot explain is a blocker, never
a footnote.

## Write traps & invariants

- Pass absolute paths to file tools; after a batch of writes, confirm each file is
  NON-EMPTY — an empty `.java`/`.yml`/`.sql` compiles clean but breaks scan/boot.
- **Never edit `web/src/lib/api.gen.d.ts`** — it is generated; change the api, then
  `pnpm -C web gen:api`.
- pnpm 11 ignores a `"pnpm"` block in `package.json` (settings live in
  `pnpm-workspace.yaml`/`.npmrc`).
- App classes are named explicitly: `NorthstarApiApplication`, `NorthstarMcpApplication`,
  `NorthstarWorkerApplication`. Package root is `com.northstar`.
