# Mobile Shell

## Scope

The Flutter client under `mobile/` provides an executable Cupertino-first shell
with native mobile token authentication, Assistant streaming, reviewed Capture
writes, and focused daily actions against the Northstar API.

## Design System

- Semantic iOS dynamic colors support light/dark appearance.
- Spacing, radius, readable-width, breakpoint, and typography decisions live in
  `mobile/lib/ui/core/design_system/`.
- Shared surfaces and empty states provide consistent borders, spacing, labels,
  and minimum interaction affordances.
- The app uses Cupertino presentation on Android and Web as well as iOS during
  this phase so the iPhone direction remains reviewable from Windows.

## Navigation

The shell exposes `Today | Study | Assistant | Finance | More` from one shared
destination model. Assistant is the initial destination and occupies the middle
position; Capture and Notes are focused routes instead of permanent tabs.

- Widths below 600 logical pixels use a native `CupertinoTabBar` around the
  route-owned branch navigator.
- Widths of at least 600 logical pixels use a Cupertino-styled sidebar.
- The selected destination survives a resize between the two layouts.
- `go_router` owns `/login`, `/today`, `/study`, `/assistant`, `/finance`, and
  `/more` through `StatefulShellRoute.indexedStack`, retaining a stack per branch.
- `/more/calendar`, `/more/habits`, `/more/account`, and `/more/settings` are
  real secondary mobile routes. `/notes/:slug` opens one Assistant-selected
  note without exposing a Notes destination.
- Protected `/capture` is a focused page opened from Assistant; it intentionally
  hides the permanent tab bar while the user drafts and reviews an item.
- A `ChangeNotifier` auth state machine redirects signed-out users to login and
  signed-in users to the intended protected destination.
- Legacy `/tasks` and `/notes` links redirect to the current daily and
  Assistant-first information architecture.

## Assistant

Assistant is the default destination. It provides authenticated conversation
history, AI SDK-compatible SSE streaming, visible waiting/tool/partial-text
states, Markdown messages, stop, retry, and a Cupertino composer. A headless
chat package owns message-list mechanics while Northstar owns the presentation,
transport, repository, and ViewModel behavior.
The Cupertino composer exposes the backend-approved model catalog for the
current conversation. Selecting a model updates the server-side conversation
route; mobile never receives gateway credentials and continues to consume the
same AI SDK-compatible SSE endpoint as web.

Flutter AI Toolkit is not part of the current client. Northstar owns a Cupertino
chat presentation connected to the existing backend rather than adding a
Firebase-owned provider path to mobile.

## Capture

- Assistant exposes a named Capture action; Capture is not a sixth tab.
- Text can be auto-classified or forced to Note, Task, Event, Expense, Study,
  or Vocabulary.
- Receipt intake uses the official system camera/photo picker and authenticated
  multipart upload. Images are used for extraction and are not stored.
- AI output is an editable proposal. The user confirms the kind-specific write,
  sees the saved result, and can undo it.
- Voice is intentionally outside this increment and requires a separate native
  recording lifecycle.

## Daily surfaces

- Today combines due and upcoming Tasks, the next Calendar event, and today's
  Habit check-ins. Task completion/reopen and habit done/excuse/clear update
  optimistically and roll back deterministically when the server rejects them.
- Study runs a focused, capped FSRS vocabulary review. The server owns due-card
  selection, direction, rating intervals, and the resulting schedule; the user
  alone records Again, Hard, Good, or Easy.
- Finance is a glance surface for the current-period summary and recent ledger
  activity. Dense ledger/configuration work stays web-first, while new entries
  remain available through Assistant and Capture.
- More contains Calendar, dedicated Habits, Account, Settings, and sign out.
  Habit schedule editing and dense history stay web-first.
- Destination opens and completed daily actions emit content-free telemetry
  names tagged with the mobile client surface; event payloads contain no note,
  message, task, or finance content.

## API context

- Mobile sends bearer authentication and an `X-Timezone` request header for
  local-date behavior. Browser preview origins are allowlisted exactly by the
  API; CORS permits this header without enabling credentialed cross-origin
  requests.
