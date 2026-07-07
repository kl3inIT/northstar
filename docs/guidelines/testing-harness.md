# Testing Harness

Code compiling is not done. Use these gates before claiming code work is
complete.

## Gate 1 - API And Static Checks

Primary, when connected:

- Verify every unfamiliar Spring, Modulith, JPA, npm, or framework symbol against
  the jar/source or `node_modules` type definitions before typing it.
- Run the IDE/static inspection for every edited file when that tool is
  connected.

Fallback:

```bash
./gradlew --no-daemon compileJava
pnpm -C web typecheck
./gradlew :core:test
```

`compileJava` and TypeScript checks are not enough for wiring, schema, or
runtime flow. Modulith verification catches module boundary violations.

## Gate 2 - Context Loads

Use a terminating command:

```bash
./gradlew --no-daemon clean test
```

This boots Spring contexts, runs Flyway/Testcontainers tests, and exits.

Do not use `bootRun` as Gate 2. It is non-terminating and can hang a session.

## Gate 3 - Runs Or Renders

For user-facing or flow changes:

- Start the required app(s).
- Poll `http://localhost:8888/actuator/health` until the API is UP when the API
  is involved.
- Drive the actual SPA with Playwright or the available browser verification
  tooling.
- Shut down background servers you started unless the user asked to keep them.

Context-load tests do not prove a new view renders or a workflow works.

## Completion Evidence

When reporting completion, include:

- Static/inspection verdict per file or file group touched.
- Test command names and outcomes.
- Runtime/render walkthrough evidence for user-facing behavior.
- Any known skipped gate and the reason.
