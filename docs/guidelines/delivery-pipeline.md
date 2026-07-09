# Delivery Pipeline

Northstar uses three gates:

1. `CI` runs the backend Gradle build/tests and the web build.
2. `Build and Push Images` runs only after CI succeeds on `main`.
3. `Deploy` runs only after image publication succeeds, on the self-hosted VPS
   runner.

## Change Scope Rules

- Docs-only and agent-guidance-only changes do not need CI, image builds, or
  deploys.
- `web/**` changes require the web build.
- `apps/api/**`, `apps/mcp/**`, and `apps/worker/**` changes require the
  backend build and the changed deployable image.
- `core/**` changes fan out to every backend deployable because API, MCP, and
  worker all depend on the shared domain library.
- Root Gradle, `build-logic/**`, `gradle/**`, and unknown root build files are
  treated as JVM-wide changes and require the full backend gate.
- `contracts/openapi.json` changes require `pnpm -C web gen:api` and the web
  build; backend contract tests should run when the contract came from API
  changes.

For this small monorepo, full backend CI on shared/root changes is preferred
over a fragile partial build.

## Image And Deploy Rules

- CI validates source. Image publication must checkout the same commit CI
  validated before generating image tags.
- Image builds publish both `:main` and `:sha-<short>`, but automatic deploys
  use the immutable `:sha-<short>` tag.
- `:main` remains useful for manual smoke/debug work only; it is not the
  auto-deploy target.
- Deployment runs under the GitHub `production` environment so secrets, vars,
  deployment history, and future approval rules have a single home.
- The deploy job snapshots running image IDs before recreate, runs health/smoke
  checks after recreate, and rolls back to the previous image IDs if the deploy
  path fails.
