# Production CORS Plan

- [x] Replace the preview-specific origin setting with a general exact-origin
  production allowlist.
- [x] Keep credentialed cross-origin requests disabled and explicitly constrain
  methods and request headers.
- [x] Validate configured origins fail closed at startup.
- [x] Expand property and authentication integration coverage for the CORS
  policy.
- [x] Run static analysis and focused integration tests.
- [x] Consolidate documentation and move this increment to completed.
- [x] Commit only the CORS scope before publishing.
