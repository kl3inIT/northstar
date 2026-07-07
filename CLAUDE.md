# Northstar - Guidance

Map of this repository's knowledge base. This file is a table of contents plus
durable workflow rules; read the linked docs for detail instead of duplicating
facts here.

## Documentation Map

- [ARCHITECTURE.md](./ARCHITECTURE.md) - current stack, deployables, module
  layout, schema ownership, build/run/test commands, and architectural
  conventions that are already true in code.
- [docs/vision.md](./docs/vision.md) - product intent, scope, future modules,
  and increment descriptions that are not yet current architecture facts.
- [docs/roadmap.md](./docs/roadmap.md) - delivery status for increments and the
  future backlog.
- [docs/conventions.md](./docs/conventions.md) - code, migration, generated
  client, and commit conventions.
- [docs/guidelines/](./docs/guidelines/) - reusable cross-cutting mechanics:
  testing harness, agent safety, current-stack traps, and asset handling.
- [docs/decisions/](./docs/decisions/) - append-only decision records. Living
  docs link to decisions for rationale; decision bodies are not rewritten.
- [docs/specs/](./docs/specs/) - current per-domain behavior and contracts.
- [docs/tests/](./docs/tests/) - per-domain test coverage matrices, mirroring
  specs and listing gaps.
- [docs/increments/](./docs/increments/) - active and completed increment
  directories. Each increment uses `design.md` and `plan.md`.

## Workflow

- For a coherent feature/foundation/refactor increment, create
  `docs/increments/active/<YYYY-MM-DD-slug>/design.md`, then `plan.md` beside
  it. Execute from the plan.
- When an increment ships, consolidate current behavior into `docs/specs/`,
  test coverage into `docs/tests/`, reusable mechanics into `docs/guidelines/`,
  and significant rationale into `docs/decisions/`. Then move the increment
  directory to `docs/increments/completed/` and update `docs/roadmap.md`.
- Small fixes and chores can skip design/plan, but not consolidation: if
  durable behavior changed, update the affected spec, test matrix, and decision
  record in the same change.
- Do not treat completed increment docs as current state. Current state is the
  sum of `ARCHITECTURE.md` plus the durable specs and guidelines.

## Documentation Hygiene

- Keep this file thin. Architecture facts belong in `ARCHITECTURE.md`; product
  intent belongs in `docs/vision.md`; unit facts belong in `docs/specs/`;
  testing coverage belongs in `docs/tests/`; reusable mechanics belong in
  `docs/guidelines/`; rationale belongs in `docs/decisions/`.
- Living docs record what is true now. Planned or intended behavior stays in
  `docs/vision.md`, `docs/roadmap.md`, or the active increment until it lands.
- A spec says what a unit is. A guideline says how a framework, platform, test
  harness, or agent workflow works.
- A decision records why a significant choice was made. If reality changes,
  update the living doc and supersede the decision with a new one.

## Verification

- Before claiming code work is done, follow
  [docs/guidelines/testing-harness.md](./docs/guidelines/testing-harness.md).
- Before typing unfamiliar symbols on the current stack, follow
  [docs/guidelines/agent-safety.md](./docs/guidelines/agent-safety.md).
- For current library/framework documentation, use Context7 instead of memory.
