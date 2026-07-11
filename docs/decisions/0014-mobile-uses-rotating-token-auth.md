# 0014 - Mobile Uses Rotating Token Auth

Status: accepted

## Context

The Flutter client runs outside the browser's same-origin cookie model. Reusing
the web session would couple native clients to CSRF and cookie behavior, while a
long-lived bearer token would increase the impact of device or token leakage.

## Decision

Mobile authentication is a separate protocol under `/api/auth/mobile`.
Northstar issues a short-lived HS256 access JWT and a cryptographically random,
opaque refresh token. Access tokens stay in app memory. Native clients persist
only the refresh token in iOS Keychain or Android encrypted storage; the API
stores only its SHA-256 hash.

Refresh tokens rotate on every use. Reuse of an old token is treated as replay
and revokes the entire token family. Logout also revokes the family. JWTs are
validated for signature, expiry, issuer, audience, and `token_use=access`.

Mobile auth is disabled by default and only starts when
`NORTHSTAR_MOBILE_AUTH_ENABLED=true` and a Base64 secret of at least 32 bytes is
provided through `NORTHSTAR_MOBILE_AUTH_JWT_SECRET`.

Flutter Web remains a Windows development-preview target and keeps its refresh
token in memory; it does not persist mobile credentials in browser storage.
When the preview calls a separate local API origin, that exact origin must be
explicitly configured; Northstar does not enable wildcard CORS.

## Consequences

The browser keeps its HttpOnly session and SPA CSRF flow unchanged. Native
clients can restore a session without retaining a long-lived access token, and
refresh replay has a bounded response. Operators must manage the JWT secret and
enable mobile auth explicitly before a device can sign in.
