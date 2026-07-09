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
| Future Flutter/mobile auth | Gap | Not implemented; token flow should get its own spec and tests when mobile starts. |
