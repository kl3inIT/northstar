# Mobile Shell

## Scope

The Flutter client under `mobile/` provides an executable Cupertino-first shell
with native mobile token authentication, Assistant streaming, and reviewed
Capture writes against the Northstar API.

## Design System

- Semantic iOS dynamic colors support light/dark appearance.
- Spacing, radius, readable-width, breakpoint, and typography decisions live in
  `mobile/lib/ui/core/design_system/`.
- Shared surfaces and empty states provide consistent borders, spacing, labels,
  and minimum interaction affordances.
- The app uses Cupertino presentation on Android and Web as well as iOS during
  this phase so the iPhone direction remains reviewable from Windows.

## Navigation

The shell exposes Assistant, Tasks, Notes, Finance, and More from one shared
destination model.

- Widths below 600 logical pixels use a native `CupertinoTabBar` around the
  route-owned branch navigator.
- Widths of at least 600 logical pixels use a Cupertino-styled sidebar.
- The selected destination survives a resize between the two layouts.
- `go_router` owns `/login`, `/assistant`, `/tasks`, `/notes`, `/finance`, and
  `/more` through `StatefulShellRoute.indexedStack`, retaining a stack per branch.
- Protected `/capture` is a focused page opened from Assistant; it intentionally
  hides the permanent tab bar while the user drafts and reviews an item.
- A `ChangeNotifier` auth state machine redirects signed-out users to login and
  signed-in users to the intended protected destination.
- More contains Calendar, Projects, Disciplines, Settings, account identity, and
  sign out. Unfinished destinations clearly state that they are planned later.

## Assistant

Assistant is the default destination. It provides authenticated conversation
history, AI SDK-compatible SSE streaming, visible waiting/tool/partial-text
states, Markdown messages, stop, retry, and a Cupertino composer. A headless
chat package owns message-list mechanics while Northstar owns the presentation,
transport, repository, and ViewModel behavior.

Flutter AI Toolkit is not part of the current client. Northstar owns a Cupertino
chat presentation connected to the existing backend rather than adding a
Firebase-owned provider path to mobile.

## Capture

- Assistant exposes a named Capture action; Capture is not a sixth tab.
- Text can be auto-classified or forced to Note, Task, Event, or Expense.
- Receipt intake uses the official system camera/photo picker and authenticated
  multipart upload. Images are used for extraction and are not stored.
- AI output is an editable proposal. The user confirms the kind-specific write,
  sees the saved result, and can undo it.
- Voice is intentionally outside this increment and requires a separate native
  recording lifecycle.
