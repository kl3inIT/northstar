# API contracts

`openapi.json` is the shared API contract, **generated** from the Spring api
(springdoc). It is the single source of truth for typed clients:

- **web** — `cd web && pnpm gen:api` → `web/src/lib/api.gen.d.ts` (openapi-typescript),
  consumed by `openapi-fetch`.
- **mobile** (later) — `openapi-generator` (dart-dio) → Dart client.

The runtime communication is plain HTTP REST + JSON; OpenAPI only provides the
types/clients so both platforms stay in sync with the backend.

> Not committed until the api exposes endpoints + springdoc. Regenerate whenever
> the api changes; commit the generated `openapi.json` so clients can regenerate.
