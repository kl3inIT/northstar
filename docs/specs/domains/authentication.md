# Authentication Spec

## Current Behavior

- `apps/api` secures REST endpoints with Spring Security 7 when
  `northstar.auth.enabled=true`.
- Authentication is single-user and configuration-backed. The API reads
  `northstar.auth.username` and `northstar.auth.password-hash` from environment
  properties and fails startup if either is missing while auth is enabled.
- The web app signs in through `POST /api/auth/login` and stores authentication
  only in the server-side HTTP session.
- `POST /api/auth/logout` clears the Spring Security context for the current
  session.
- `GET /api/auth/me` reports whether the current request is authenticated.
- `GET /api/auth/csrf` exposes the SPA CSRF token metadata and causes the
  browser-readable `XSRF-TOKEN` cookie to be available.
- State-changing same-origin API requests must send the CSRF token in the
  `X-XSRF-TOKEN` header.
- Unauthenticated protected API requests return RFC 9457-style
  `application/problem+json` with status `401`; forbidden requests return
  status `403`. They do not redirect to an HTML login page.
- Static SPA assets, health checks, OpenAPI docs, and the auth bootstrap
  endpoints are publicly reachable.
- In tests, auth is disabled by default through `apps/api/src/test/resources`
  so existing domain integration tests can exercise business behavior without
  login boilerplate. Dedicated auth tests enable it explicitly.
- Native mobile clients use `/api/auth/mobile/login`, `/refresh`, `/logout`, and
  `/me`. Mobile auth is opt-in through `northstar.auth.mobile.enabled`.
- Mobile access JWTs are short-lived and validated for issuer, audience, expiry,
  and token use. Refresh tokens are opaque, stored hashed, rotated on every use,
  and revoked as a family on replay or logout.
- Flutter keeps the access token in memory and the native refresh token in
  Keychain/secure storage. The Web preview intentionally uses memory only.
- Native clients do not use CORS. Cross-origin browser clients are disabled by
  default and require exact HTTP(S) origins in the comma-separated
  `NORTHSTAR_CORS_ALLOWED_ORIGINS` environment value. Credentialed CORS and
  wildcard origins are not supported; browser previews use Bearer tokens.

## Source Modules

- `apps/api` auth delivery and Spring Security configuration
- `web` login page, session guard, and CSRF-aware API fetch wrapper
- `mobile` route guard, auth view model, repository, HTTP service, and secure
  refresh-token store

## Related Decisions

- [0006 - Web Uses Session Auth First](../../decisions/0006-web-uses-session-auth-first.md)
- [0014 - Mobile Uses Rotating Token Auth](../../decisions/0014-mobile-uses-rotating-token-auth.md)
