# Agent Safety

Northstar uses recent framework versions where older muscle memory is often
wrong. Verify symbols and behavior from the current repo or current docs before
making changes.

## Current Documentation

- Use Context7 for current library, framework, SDK, API, CLI, and cloud-service
  documentation.
- Prefer local repo source, Gradle cache jars/sources, and `node_modules` type
  definitions for exact symbols used by this project.
- Use web search only when the needed information is not local and not available
  through Context7 or primary docs.

## Common Stack Traps

- Spring Boot 4 moved package names and starter names. Verify annotations and
  starters before typing them.
- Testcontainers 2 package names differ from older examples.
- Deprecated symbols are blockers, not harmless warnings, when a current symbol
  exists.
- A duplicated top-level key in `application.yml` silently overrides earlier
  config.
- A JPA field without a Flyway column compiles but fails at context load because
  Hibernate validates the schema.
- A stale generated OpenAPI client can compile in backend-only checks and fail
  in the web build.

## Failure Stance

If the suite was green before a change and fails after the change, assume the
change caused it until proven otherwise. A red gate that cannot be explained is a
blocker, not a footnote.

## Write Invariants

- Pass absolute paths to file tools where possible.
- After batch writes, confirm important files are non-empty.
- Do not edit generated files directly.
- Do not revert unrelated user changes in a dirty worktree.
