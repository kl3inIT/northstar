# Delivery Pipeline

Northstar uses three gates:

1. `CI` maps changed files to backend and web areas, then runs only the selected
   Gradle and/or web gates. Unknown or shared pipeline changes run both.
2. `Build and Push Images` runs only after CI succeeds on `main`.
3. `Deploy` runs only after image publication succeeds, on the self-hosted VPS
   runner.

## Repository Guardrails

- CI declares a read-only `GITHUB_TOKEN`; image publication is the only
  workflow with `packages: write`.
- GitHub secret scanning, push protection, vulnerability alerts, and Dependabot
  security updates are enabled. Weekly dependency updates remain configured in
  `.github/dependabot.yml`.
- The `production` environment accepts workflow runs from `main` only. It has
  no reviewer or wait timer so the single-user automatic deployment path stays
  fast.
- Reusable actions use explicit release tags and Dependabot tracks them. This
  repository intentionally does not enforce full-SHA action references.

## Change Scope Rules

- Docs-only and agent-guidance-only changes do not need CI, image builds, or
  deploys.
- Mobile-only changes are owned by `Mobile CI` and do not start the JVM/web
  delivery chain.
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

The web gate regenerates the API client, runs lint and colocated Vitest tests,
builds the typed production bundle, then runs the single critical Chromium
Playwright regression. CI installs only Chromium's headless shell and uploads
the report only on failure.

## Image And Deploy Rules

- CI validates source. Image publication must checkout the same commit CI
  validated before generating image tags.
- Image builds run in parallel and store their large `mode=max` BuildKit caches
  as per-component GHCR `:buildcache` manifests. GitHub Actions cache remains
  available for Gradle, Flutter, Dart and pnpm rather than thrashing its 10 GiB
  repository budget.
- Image builds publish both `:main` and `:sha-<short>`, but automatic deploys
  use the immutable `:sha-<short>` tag.
- `:main` remains useful for manual smoke/debug work only; it is not the
  auto-deploy target.
- Deployment runs under the GitHub `production` environment so secrets, vars,
  deployment history, and branch policy have a single home. Only workflow runs
  from `main` may target that environment; no manual reviewer delays the
  single-user automatic delivery path.
- The deploy job snapshots running image IDs before recreate, runs health/smoke
  checks after recreate, and rolls back to the previous image IDs if the deploy
  path fails.
