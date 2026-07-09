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

## Source Modules

- `apps/api` auth delivery and Spring Security configuration
- `web` login page, session guard, and CSRF-aware API fetch wrapper

## Related Decisions

- [0006 - Web Uses Session Auth First](../../decisions/0006-web-uses-session-auth-first.md)
