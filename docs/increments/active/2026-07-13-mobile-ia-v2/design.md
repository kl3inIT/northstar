# Mobile IA V2 — Design

## Goal

Turn the existing Cupertino mobile foundation into a useful iPhone-first
Northstar client. The mobile app must prioritize frequent, touch-friendly work
instead of copying every web workspace: act on today's Tasks and Habits, ask the
Assistant, review Study material, glance at Finance, and reach secondary
workflows without turning each backend domain into a tab.

## Product boundary

- Assistant remains the default route and the user's primary intent surface.
- Capture is an input action, not a top-level navigation destination.
- Notes are retrieval infrastructure for Assistant. They keep a detail route
  for an explicitly opened result but do not occupy permanent navigation.
- Mobile owns quick review and action flows. Dense configuration, bulk editing,
  provider settings, large finance tables, project boards, and automation
  authoring remain web-first.
- A top-level tab is not released as a placeholder. Each visible destination
  must provide at least one complete, recoverable workflow.
- Provider credentials, model orchestration, authorization, and tool execution
  remain on the Northstar backend.

## Decisions

### D1 — Five initial destinations are an instrumented product hypothesis

The compact tab order is:

1. `Today`
2. `Study`
3. `Assistant`
4. `Finance`
5. `More`

Assistant is the initial route and occupies the middle destination without
special FAB treatment. `StatefulShellRoute.indexedStack` preserves the
navigation and scroll state of each branch. Expanded layouts reuse the same
destination model in a sidebar.

The ordering is a starting hypothesis informed by production web behavior and
the owner's stated mobile jobs, not a claim about mobile analytics before the
mobile client is used. Privacy-preserving events record destination opens and
completed actions without prompt, note, or financial content so the ordering
can be revisited after real iPhone use.

`More` is a deliberate grouped directory, not an automatic overflow tab. It
contains real secondary mobile routes: Calendar, dedicated Habit check-ins,
Account, and Settings. Projects, Disciplines, Briefs, and note detail links are
added only when their mobile workflow exists. Habit schedule editing and dense
history remain web-first. Calendar remains routable but is not a permanent tab.

### D2 — Capture moves into the Assistant input affordance

The Assistant composer exposes one labelled add button. It opens a Cupertino
action sheet for supported inputs such as quick text and receipt image, then
pushes the existing focused Capture route. Capture remains outside the shell so
reviewing a draft is a short, self-contained task.

The mobile Capture contract is brought back in sync with the backend by adding
`STUDY` and `VOCAB` alongside note, task, event, and expense. Every kind receives
typed DTO, domain, editable review, explicit save, success echo, and undo
handling. Unknown future kinds fail as a typed unsupported response rather than
crashing enum parsing.

Voice, Share Sheet, Home Screen Quick Actions, and Action Button integration
remain follow-up platform increments; the architecture must not block them.

### D3 — Top-level tabs provide focused vertical slices

- Today combines the next Calendar event, due Tasks, and server-derived Habit
  check-ins. It completes/reopens a task and marks a habit done/excused/clear
  with visible progress and deterministic rollback on failure.
- Study starts with the due vocabulary queue and an explicit review session.
  Rich audio and speaking practice become focused follow-up flows instead of a
  four-tab copy of the web workspace.
- Finance starts with balances, current-period spending, and recent activity;
  dense ledger editing and configuration stay web-first while Assistant remains
  the fastest transaction-entry surface.
- More exposes Calendar and full Habits as real routes plus Account/Settings.
  Assistant-opened note results use a focused detail route rather than a Notes
  tab.

### D4 — Flutter layers keep transport and UI separate

Each domain follows the existing Northstar structure:

- immutable domain models;
- raw API DTOs and stateless services;
- repositories that validate and map transport data;
- `ChangeNotifier` ViewModels with immutable exposed state;
- lean Cupertino views.

No widget calls an API or decodes model output from `build()`. Constructor
injection remains the default; no new state-management or dependency-injection
package is added for this increment.

### D5 — Layout adapts to constraints and preserves state

Compact is under 600 logical pixels, medium is 600–839, and expanded is 840 or
wider. Layout decisions use `MediaQuery.sizeOf` or local `LayoutBuilder`
constraints, never device names or orientation checks. Reading and form content
uses deliberate maximum widths on expanded layouts.

The app preserves selected tab, nested navigation, scroll position, composer
text, and in-progress review state across resize and rotation where Flutter can
retain the route.

### D6 — AI and accessibility states are part of the feature

Model output is untrusted. Capture validates typed output, keeps generated
fields editable, and requires explicit save. Assistant and Capture expose
specific waiting, partial, error, cancellation, retry, confirmation, and undo
states. Consequential destructive, financial, or privacy-sensitive actions
remain server-authorized and require confirmation or a clear recovery path.

All controls receive semantic labels and usable touch targets. Text supports
scaling, dynamic Cupertino colors cover light/dark mode, and focus/keyboard
accelerators are additive on wider layouts.

## Existing backend contracts reused

- `/api/capture/draft`, `/api/capture/receipt`
- `/api/tasks/today`, `/api/tasks/upcoming`, `/api/tasks/{id}/status`
- `/api/habits/today`, `/api/habits/{id}/check-ins/{date}`
- `/api/calendar/events`
- `/api/finance`, `/api/finance/summary`
- `/api/study/vocab/review`, `/api/study/vocab/{id}/reviews`
- `/api/notes`, `/api/notes/search`, `/api/notes/{slug}`
- `/api/briefs/huggingnews`, `/api/briefs/huggingnews/{topic}/{slug}`

Timezone-sensitive requests carry `X-Timezone`. Mobile reuses the authenticated
client's access-token refresh and unauthorized-session handling.

## Verification

1. DTO and repository tests cover valid, malformed, unknown, empty, and error
   responses for every added contract.
2. ViewModel tests cover loading, success, empty, error, retry, cancellation,
   confirmation, save, and undo where applicable.
3. Widget tests cover compact and expanded navigation, tab-state preservation,
   dark mode, text scaling, semantics, and the primary interaction for every
   visible tab.
4. `dart format`, `flutter analyze`, and `flutter test` end green after every
   implementation block.
5. A real local API walkthrough covers login, Assistant, all Capture kinds,
   Today, Calendar routing, Finance, task completion, habit check-in, and
   vocabulary review.
6. GitHub Actions builds Web, Android, unsigned iOS, and the Sideloadly IPA.
   Native iPhone accessibility, microphone, and installation checks remain
   explicitly reported until performed on hardware.
