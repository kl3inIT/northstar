# Habit Tracking — Plan

## Block A — Domain and REST contract

- Add this design, decision record and Flyway schema.
- Implement Habit, effective schedule, check-in and pause aggregates plus
  derived Today/insight read models.
- Add core tests and REST integration tests.
- Regenerate OpenAPI and the TypeScript client.
- End green on backend compile/core/API tests and web typecheck.
- Commit: `feat(habits): add habit tracking domain`.

## Block B — Assistant and review integration

- Add shared Assistant/MCP tools for full non-destructive habit workflow.
- Include real weekly habit facts in Alignment review generation.
- Add focused tool and review-facts tests.
- End green on backend compile/core/API/MCP tests.
- Commit: `feat(assistant): add habit tools and review facts`.

## Block C — Responsive Habits work surface

- Add route/navigation and a typed React Query API adapter.
- Build Today, All habits and Insights views with one-tap check-in, schedule
  editing, pause/archive and contribution history.
- Add focused frontend logic tests and responsive states.
- End green on web lint, tests, typecheck and production build.
- Commit: `feat(web): add habit tracking workspace`.

## Block D — Runtime verification and consolidation

- Run the terminating full backend/web gates.
- Walk create/check/undo/pause/resume/edit/archive and Insights in a real
  desktop and mobile browser, fixing functional and visual findings.
- Update durable spec, test matrix, roadmap, architecture decision and the
  `Northstar App Behavior` note.
- Move this increment to completed.
- Commit: `docs(habits): consolidate habit tracking`.

