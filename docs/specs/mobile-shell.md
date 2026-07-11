# Mobile Shell

## Scope

The Flutter client under `mobile/` provides an executable Cupertino-first shell
with native mobile token authentication. Product data and Assistant streaming
are not connected yet.

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
- A `ChangeNotifier` auth state machine redirects signed-out users to login and
  signed-in users to the intended protected destination.
- More contains Calendar, Projects, Disciplines, Settings, account identity, and
  sign out. Unfinished destinations clearly state that they are planned later.

## Assistant Landing

Assistant is the default destination. Its landing view introduces Capture,
planning, and finance entry points and shows a disabled composer with an explicit
message that Assistant API integration is not connected.
The app does not simulate a successful AI interaction.

Flutter AI Toolkit is not part of the current client. Northstar owns a Cupertino
chat presentation and will connect it to the existing backend assistant rather
than adding a Firebase-owned provider path to mobile.
