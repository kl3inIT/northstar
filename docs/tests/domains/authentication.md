# Authentication Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Protected API requests reject anonymous callers with ProblemDetail JSON | Automated | `apps/api/src/test/java/com/northstar/api/auth/AuthControllerIntegrationTests.java` |
| JSON login requires CSRF and rejects bad credentials | Automated | `AuthControllerIntegrationTests` covers missing CSRF, wrong password, and successful login. |
| Successful login persists a durable server-side session | Automated | `AuthControllerIntegrationTests` verifies the 30-day `SESSION` cookie, the 30-day JDBC session row, then reads `/api/notes` with that cookie. Flyway creates the Spring Session schema in PostgreSQL. |
| Logout clears the authenticated session view | Automated | `AuthControllerIntegrationTests` logs out, then verifies `/api/auth/me` is anonymous. |
| Auth-disabled local development bypasses the SPA login screen | Automated | `AuthControllerTests` verifies `/api/auth/me` exposes the synthetic `local` session only when `northstar.auth.enabled=false`. |
| SPA CSRF bootstrap exposes `XSRF-TOKEN` | Automated | `AuthControllerIntegrationTests` covers `/api/auth/csrf` and the browser-readable cookie. |
| Existing domain tests can run without auth boilerplate | Automated | `apps/api/src/test/resources/application.yml` disables auth for the default test context. |
| Browser login/logout flow | Gap | Needs Playwright coverage once the app has a stable authenticated test fixture. |
| Mobile login does not require browser CSRF and returns access/refresh tokens | Automated | `AuthControllerIntegrationTests` enables mobile auth explicitly and verifies the token response. |
| Bearer access authorizes a protected mobile request | Automated | The issued JWT reads `/api/auth/mobile/me`; issuer, audience, expiry, and token-use validation are wired through Spring Security Resource Server. |
| Refresh rotation and replay response | Automated | A refresh token rotates once; replaying it returns 401 and revokes the replacement token family. |
| Mobile logout revokes refresh credentials | Automated | Logout returns 204 and the same refresh token can no longer rotate. |
| Flutter startup, login, route guard, branch navigation, and logout | Automated | `mobile/test/app_test.dart` covers signed-out redirect, successful login, five-branch shell behavior, and logout redirect. |
| Cross-origin browser access is production-allowlisted | Automated | `AuthControllerIntegrationTests` permits multiple exact origins, rejects suffix-spoofed origins and unlisted headers, supports standard REST methods, and confirms credentialed CORS is disabled. |
