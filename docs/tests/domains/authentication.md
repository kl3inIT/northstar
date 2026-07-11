# Authentication Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Protected API requests reject anonymous callers with ProblemDetail JSON | Automated | `apps/api/src/test/java/com/northstar/api/auth/AuthControllerIntegrationTests.java` |
| JSON login requires CSRF and rejects bad credentials | Automated | `AuthControllerIntegrationTests` covers missing CSRF, wrong password, and successful login. |
| Successful login persists a server-side session | Automated | `AuthControllerIntegrationTests` logs in, then reads `/api/notes` with the saved session. |
| Logout clears the authenticated session view | Automated | `AuthControllerIntegrationTests` logs out, then verifies `/api/auth/me` is anonymous. |
| SPA CSRF bootstrap exposes `XSRF-TOKEN` | Automated | `AuthControllerIntegrationTests` covers `/api/auth/csrf` and the browser-readable cookie. |
| Existing domain tests can run without auth boilerplate | Automated | `apps/api/src/test/resources/application.yml` disables auth for the default test context. |
| Browser login/logout flow | Gap | Needs Playwright coverage once the app has a stable authenticated test fixture. |
| Mobile login does not require browser CSRF and returns access/refresh tokens | Automated | `AuthControllerIntegrationTests` enables mobile auth explicitly and verifies the token response. |
| Bearer access authorizes a protected mobile request | Automated | The issued JWT reads `/api/auth/mobile/me`; issuer, audience, expiry, and token-use validation are wired through Spring Security Resource Server. |
| Refresh rotation and replay response | Automated | A refresh token rotates once; replaying it returns 401 and revokes the replacement token family. |
| Mobile logout revokes refresh credentials | Automated | Logout returns 204 and the same refresh token can no longer rotate. |
| Flutter startup, login, route guard, branch navigation, and logout | Automated | `mobile/test/app_test.dart` covers signed-out redirect, successful login, five-branch shell behavior, and logout redirect. |
| Flutter Web preview CORS is allowlisted | Automated | `AuthControllerIntegrationTests` permits the configured local origin and rejects an untrusted origin. |
