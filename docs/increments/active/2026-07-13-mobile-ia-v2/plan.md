# Mobile IA V2 — Plan

## Block A — Contract alignment and increment harness

**Status:** Complete — 11 focused Capture tests and the 36-test full suite
passed before commit `b4fb146`.

- Add the design and ordered plan.
- Inventory current mobile/backend contract drift.
- Add `STUDY` and `VOCAB` Capture DTO/domain/repository mappings with an explicit
  unsupported-kind failure.
- Add focused unit tests before changing navigation.
- End green on Dart format, Flutter analysis, and Capture tests.
- Commit: `fix(mobile): align Capture with current backend kinds`.

## Block B — Reviewed Capture entry from Assistant

**Status:** Complete — Dart analysis reported no errors; 19 focused tests and
the 42-test full suite passed.

- Replace the ambiguous navigation-bar Capture shortcut with a labelled add
  affordance in the Assistant composer.
- Present a Cupertino input-source action sheet and reuse the focused Capture
  route.
- Add editable Study and Vocab review/save/undo views.
- Cover compact, dark, loading, error, retry, save, and undo states.
- End green on focused widget tests plus full Flutter analysis/tests.
- Commit: `feat(mobile): extend reviewed Capture from Assistant`.

## Block C — Today daily actions

**Status:** Complete — 21 focused contract/API/repository/ViewModel/widget tests
and the 55-test full Flutter suite passed; Dart analysis reported no errors.

- Add typed services, repositories, models, and ViewModels for Tasks Today /
  Upcoming, the next Calendar event, and Habits Today.
- Implement task completion/reopen plus habit done/excuse/clear with optimistic
  progress and deterministic rollback on failure.
- Add empty, offline/error, retry, dark, compact, and expanded states.
- End green on unit/widget tests plus full Flutter analysis/tests.
- Commit: `feat(mobile): add Tasks and Habits daily flows`.

## Block D — Focused Study review and Finance glance

- Add the due vocabulary queue, direction-aware card state, answer reveal, real
  interval previews, and learner-owned FSRS rating submission.
- Preserve session progress across rebuilds and make completion/empty/error
  states explicit.
- Keep audio playback and speaking recording behind follow-up focused flows
  unless the current backend contract can be integrated and verified safely in
  this block.
- Add a typed Finance summary/recent-activity read path. Keep transaction entry
  Assistant-first and dense ledger/configuration web-first.
- End green on repository, ViewModel, widget, and full Flutter gates.
- Commit: `feat(mobile): add Study review and Finance glance`.

## Block E — Stable shell and secondary routes

- Change the shared destination model and shell to `Today | Study | Assistant |
  Finance | More`, keeping Assistant as the default route.
- Preserve independent branch stacks with `StatefulShellRoute.indexedStack`.
- Implement Calendar and full Habits as routable screens under More, alongside
  Account and Settings. Keep note detail routable from Assistant results without
  exposing a Notes tab.
- Add content-free interaction telemetry for destination opens and completed
  actions, tagged by client surface.
- Validate compact, medium, and expanded navigation plus text scaling and
  semantics.
- End green on navigation/widget tests and the full Flutter gates.
- Commit: `feat(mobile): ship the daily Cupertino navigation shell`.

## Block F — Runtime verification and consolidation

- Run the terminating mobile and relevant backend gates.
- Walk login, Assistant, Capture, Today, Study, Finance, Calendar, Habits, and
  More against a real local API at compact and expanded widths.
- Run the GitHub Actions iOS/IPA path and report native-device-only gaps.
- Consolidate current behavior into durable specs/tests, update roadmap and the
  `Northstar App Behavior` note, and move this increment to completed.
- Commit: `docs(mobile): consolidate Mobile IA V2`.
