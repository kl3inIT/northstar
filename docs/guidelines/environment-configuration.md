# Environment Configuration

Northstar has one Spring Boot deployable, `northstar-server`, which owns the
runtime profile shape for REST, MCP, migrations, and jobs:

- `application.yml` contains environment-independent behavior and safe
  defaults;
- `application-local.yml` imports the repo `.env` and may enable developer-only
  diagnostics;
- `application-prod.yml` defines production database, logging, proxy,
  management, and shutdown policy.

Activate profiles at the delivery boundary. Checked-in IntelliJ runs use
`local`; Docker Compose sets `SPRING_PROFILES_ACTIVE=prod`. Do not set
`spring.profiles.active` inside packaged YAML and do not put it in `.env`: the
local profile must already be active before that profile can import `.env`.

## Secrets

YAML contains placeholders and non-secret defaults only. Local secrets live in
the gitignored `.env`; production secrets live in the server `.env` consumed by
Compose. Every environment owns a different stable
`NORTHSTAR_AI_CREDENTIAL_KEY`; deployment never copies the local key to the
server. Provider keys should normally be saved from `Settings > AI models` and
are encrypted in PostgreSQL. An environment provider key such as
`OPENAI_API_KEY` is an optional bootstrap/recovery fallback, not a startup
requirement. The master encryption key remains server-owned because storing it
beside the ciphertext it protects would remove the security boundary.

## Database Pools

The unified server owns one Hikari pool, named `northstar-server`, capped at 10
connections by default. Tune maximum and minimum sizes from
PostgreSQL capacity and pool metrics, not request concurrency or virtual-thread
count. Keep Hikari lifetime and keepalive defaults until an upstream database,
proxy, or network timeout provides a concrete lower bound.

## Logging And Management

Production writes ECS JSON to stdout and Docker performs log rotation. The
Spring AI `SimpleLoggerAdvisor` is `OFF` in base/production because its payloads
can contain private note, finance, study, and conversation content; only
`local` enables it at `DEBUG`.

Production exposes only Actuator health. The local server additionally exposes info
and Modulith inspection. Never broaden production exposure without matching
authorization and a concrete operational consumer.

## Shutdown

Spring Boot graceful shutdown is explicit in production. db-scheduler can use
two one-minute executor waits, and Spring scheduling has a separate two-minute
await-termination period. Compose therefore provides a five-minute outer stop
budget: four minutes for the sequential worst case plus one minute of margin
before Docker sends `SIGKILL`. The 130-second Spring timeout remains a per-phase
bound rather than being mistaken for the whole container budget.

References:

- [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot structured logging](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured)
- [Spring Boot graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html)
- [HikariCP configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
