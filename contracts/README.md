# API contracts

`openapi.json` is the shared API contract, **generated** from the Spring api
(springdoc). It is the single source of truth for typed clients:

- **web** — `cd web && pnpm gen:api` → `web/src/lib/hey-api/` (Hey API
  TypeScript types, Fetch API client, and SDK functions). Runtime configuration
  lives in `web/src/lib/hey-api-config.ts` and uses Hey's `client.setConfig()`
  to route generated requests through the app's CSRF/session-aware `apiFetch`.
- **mobile** (later) — `openapi-generator` (dart-dio) → Dart client.

The runtime communication is plain HTTP REST. Most endpoints are JSON; file and
voice upload endpoints are multipart/form-data. OpenAPI provides the typed
clients so platforms stay in sync with the backend.

> Not committed until the api exposes endpoints + springdoc. Regenerate whenever
> the api changes; commit the generated `openapi.json` so clients can regenerate.
> Generated client code stays out of git.
