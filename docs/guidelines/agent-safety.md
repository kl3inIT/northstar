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

## Reference Repositories

- [`spring-ai-community/spring-ai-agentcore`](https://github.com/spring-ai-community/spring-ai-agentcore)
  is a reference implementation for typed memory, sampled evaluations,
  observability, session-scoped artifacts, browser sessions, and code
  interpreter lifecycle. When work touches one of those areas, an agent may
  shallow-clone the repository under `.tmp/`, record the inspected commit in
  the increment research, read the relevant module and tests, then remove the
  clone. Learn the pattern; do not import its AWS runtime contract or copy the
  implementation wholesale.
- For cache work specifically, the useful AgentCore pattern is its
  `ArtifactStore` boundary: session plus category scoping, atomic concurrent
  append, destructive `retrieve` versus non-destructive `peek`, automatic TTL,
  a maximum entry bound, immutable artifacts, and an implementation factory.
  Treat this as temporary artifact lifecycle, not as a semantic response
  cache. Northstar also needs byte-size limits and a multi-instance-capable
  provider before using the pattern for large files in production.
- [`habuma/spring-ai-recipes`](https://github.com/habuma/spring-ai-recipes)
  is a companion reference for Spring AI semantic caching, including the
  VectorStore-backed variant. Spring AI's current official docs and local API
  symbols remain the authority; the recipe is an example, not a dependency or
  architecture decision.

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
