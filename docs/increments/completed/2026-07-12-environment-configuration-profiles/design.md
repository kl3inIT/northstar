# Environment Configuration Profiles

## Problem

API, MCP, and worker currently keep local conveniences and production behavior
in one `application.yml`. That makes full AI request logging, local `.env`
imports, actuator exposure, cookie security, database pool sizing, and shutdown
behavior depend on implicit defaults or operator memory.

## Decision

- Each deployable owns `application.yml`, `application-local.yml`, and
  `application-prod.yml`; configuration remains explicit per process instead of
  being hidden in a cross-application shared YAML.
- Base configuration contains environment-independent behavior and safe
  defaults. Local profiles import the repo `.env` and enable developer-only
  diagnostics. Production profiles define pool budgets, structured logging,
  proxy/cookie behavior, actuator exposure, and shutdown timeouts.
- Docker Compose activates `prod` explicitly. Checked-in IntelliJ run
  configurations activate `local` explicitly. No application silently chooses
  a deployment profile.
- Hikari pools have separate names and budgets for API, MCP, and worker. Pool
  sizes and acquisition timeouts remain environment-overridable; connection
  lifetime and keepalive retain Hikari defaults until infrastructure evidence
  justifies changing them.
- Spring Boot's default graceful shutdown remains enabled. Compose stop grace
  periods are made longer than the configured Spring/db-scheduler shutdown
  windows so Docker does not terminate a process mid-drain.

## Safety Properties

- Production cannot enable full LLM prompt/response logging through a packaged
  default.
- Production session cookies default to `Secure` and forwarded headers are
  interpreted only in the production profile.
- Production exposes only Actuator health over HTTP.
- Secrets remain environment variables; YAML contains no credentials.
- Tests retain their small Testcontainers pools and do not activate production
  behavior.
