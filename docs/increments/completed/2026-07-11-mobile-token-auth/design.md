# Mobile Token Authentication

## Problem

The Cupertino shell has no authenticated API boundary and its navigation is
local widget state. A mobile client must not reuse the browser's session cookie
and CSRF flow, and route protection must be deterministic during startup,
login, token refresh, and logout.

## Decision

Northstar mobile uses a separate token protocol:

- short-lived signed access tokens are held in memory only;
- opaque refresh tokens are stored in iOS Keychain or Android encrypted
  storage and only their SHA-256 hashes are persisted by the API;
- every refresh rotates the token; replay revokes the whole token family;
- browser session authentication remains unchanged;
- Web is a development preview target and keeps refresh state in memory rather
  than browser storage.

The Flutter app uses `CupertinoApp.router` and `go_router`. A
`StatefulShellRoute.indexedStack` owns the five product branches so each branch
keeps its navigation stack. A `ChangeNotifier` authentication view model is the
single redirect source for `/login` and authenticated routes.

## Layers

- `data/services`: HTTP transport and refresh-token storage.
- `data/repositories`: token lifecycle and authenticated session boundary.
- `domain/models`: immutable app-facing authentication state.
- `ui/features/auth`: state machine and Cupertino login UI.
- `ui/core/navigation`: route table, redirect policy, and adaptive shell.

No widget reads secure storage or performs HTTP directly. The API base URL is a
compile-time environment value (`NORTHSTAR_API_BASE_URL`) with localhost as the
development default.

## API Contract

- `POST /api/auth/mobile/login`
- `POST /api/auth/mobile/refresh`
- `POST /api/auth/mobile/logout`
- `GET /api/auth/mobile/me`

Login and refresh return the access token, its expiry, the rotated refresh
token, and the authenticated username. Mobile auth endpoints do not accept or
create browser sessions and are excluded from CSRF because authentication is
carried explicitly in request bodies or bearer headers.

## Verification

- backend integration tests cover login, bearer access, rotation, replay, and
  logout while existing browser-session tests remain green;
- Flutter tests cover startup restore, redirect, login failure/success, logout,
  and branch navigation;
- `dart format`, `flutter analyze`, `flutter test`, Web release build, and the
  relevant Gradle tests must pass.
