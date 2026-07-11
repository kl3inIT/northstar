# Mobile Token Authentication Plan

- [x] Inspect the existing browser auth, Flutter shell, and platform projects.
- [x] Define the separate mobile token protocol and route ownership.
- [x] Add official Flutter routing, HTTP, and secure-storage packages.
- [x] Add the API mobile auth configuration, migration, token service, and endpoints.
- [x] Protect bearer requests without changing browser session behavior.
- [x] Refactor the Flutter shell to `StatefulShellRoute.indexedStack`.
- [x] Add the repository, auth view model, secure store, and Cupertino login UI.
- [x] Add backend and Flutter tests for the complete lifecycle.
- [x] Run static analysis, tests, Web build, and consolidate durable documentation.

Android source/toolchain integration is configured, but a local debug APK build
remains a machine-level validation gap: the Windows Flutter/Gradle process hung
after an initial virtual-memory failure. No Gradle cache was deleted. iOS compile,
signing, Keychain behavior, and device accessibility remain CI/device gates.
