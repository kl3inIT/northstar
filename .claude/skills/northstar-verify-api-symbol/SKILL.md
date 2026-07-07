---
name: northstar-verify-api-symbol
description: Before typing a class, annotation, package path, starter/artifact coordinate, method, or npm export you have not personally verified in the Northstar project, confirm it exists. Costs seconds and prevents the most expensive class of failures — hallucinated or moved symbols (e.g. Boot 4 relocated @EntityScan and renamed starters vs Boot 3 muscle memory) that survive typing and then break compile or app boot. Use this whenever you are about to write an unfamiliar Spring / Spring Modulith / JPA / Flyway import or a web (TanStack/openapi-fetch/Tailwind) API you have not already seen in this repo's source. Primary check = read the dependency's own jar/sources in the Gradle cache (or node_modules .d.ts); Context7 is for usage snippets and patterns only, never the authority on a real symbol's existence or signature.
---

# Verify API symbols before you type them

A top cause of failed runs is confidently typed API names that do not exist —
they look right, survive typing, then break `compileJava`/`tsc` or, worse, only at
app boot. This is a seconds-long pre-flight check. Run it BEFORE writing any symbol
you have not already seen in this repo's source tree. It is especially worth it on
this stack because it rides recent major versions (Spring Boot 4, Spring Modulith
2, Tailwind 4, React 19) where Boot-3 / Tailwind-3 muscle memory is actively wrong.

## When to run

Before typing any of:

- A Spring annotation qualified by a guessed package — Boot 4 moved several
  (is `@EntityScan` in `...autoconfigure.domain` or `...persistence.autoconfigure`?).
- A Gradle starter / artifact coordinate you are adding to a `build.gradle.kts`
  (did the starter get renamed or split in Boot 4?).
- A Spring Modulith type or the exact shape a call requires
  (`ApplicationModules.of(...)` on what kind of class?).
- A method on a Spring Data repository, a Flyway config key, or a bean/`@Value`
  property name.
- An npm package export in `web/` — a default vs named export, or a
  TanStack Router / openapi-fetch / Tailwind API you have not used here yet.

Skip it only when you can point at the same exact symbol already in use in this
repo's `core/`, `apps/`, or `web/src/`.

## How to verify — the jar first, Context7 for patterns only

**Free short-circuit:** if the EXACT symbol is already used in this repo, copy that
call site — it is ground truth, no lookup needed:

```bash
# backend
grep -rn "@EntityScan\|ApplicationModules\|@Modulithic" core apps --include='*.java'
# a starter/artifact already declared somewhere
grep -rn "spring-boot-starter" . --include='*.kts'
# web export already imported
grep -rn "from \"@tanstack/react-router\"\|openapi-fetch" web/src
```

For anything NOT already in the repo, verify it before you type it:

1. **Read the dependency's own code — PRIMARY for real API shape.** Docs sites (and
   Context7 snippets scraped from them) lag releases and carry stale examples; the
   artifact you actually compile against cannot lie. Confirm the class, its package,
   the method signature, and deprecation status straight from the jar:
   ```bash
   # locate the artifact (prefer the *-sources.jar — it has Javadoc + @Deprecated info)
   find "$HOME/.gradle/caches/modules-2" -name 'spring-ai-openai-*-sources.jar'
   # list classes / find where a symbol really lives
   unzip -l <jar> | grep -i "EntityScan"
   # read the real source of a class
   unzip -p <sources.jar> org/springframework/ai/openai/OpenAiChatModel.java | grep -n -A5 "media"
   # exact method signatures from a binary-only jar
   unzip -o <jar> 'org/.../SearchRequest.class' -d /tmp/x && javap -cp /tmp/x org....SearchRequest
   ```
   - If the artifact is not in the cache yet, download it from Maven Central first
     (`curl -sLO https://repo1.maven.org/maven2/<group-path>/<artifact>/<version>/...`).
   - Web: `node_modules/<pkg>/dist/*.d.ts` is the authoritative export list.

2. **Context7 — snippets and patterns ONLY.** Use it to discover *how* an API is
   meant to be composed (a config recipe, an advisor wiring example, a migration
   guide) or to find *which* class/property to go look up. Do NOT treat a Context7
   snippet as proof that a signature exists or is current — this session it served
   1.0-era `SearchRequest.query().withTopK(...)` from stale Javadoc while the 2.0 jar
   has a builder. Every symbol a snippet gives you still goes through step 1 before
   you type it.

3. **Floor: grep a known-good example in the wider ecosystem / a generated
   reference** and copy its exact shape. For this repo, a project generated fresh
   from `start.spring.io` (Boot 4.1, Gradle-Kotlin) is a reliable oracle for correct
   starter names and generated `build.gradle.kts` shape.

Never invent and ship. If nothing confirms a symbol, do not type it — pick one you
CAN confirm, or drop the optional bit rather than guess.

## Confirming a symbol also means: is it the CURRENT one?

A symbol existing is not enough — a deprecated symbol still compiles (with a
warning) and still runs, so it slips through `compileJava`, but a warning today is a
removal in the next major. This stack sits on fresh majors (Boot 4, Testcontainers 2,
Tailwind 4) that renamed/moved things and left deprecated shims behind, so the
from-memory name is often the shim, not the replacement. When you look a symbol up:

- **The jar's own sources** are the ground truth for `@Deprecated` — the Javadoc spells
  out the replacement:
  ```bash
  # unzip the *-sources.jar from the Gradle cache and read the class header
  grep -iE "deprecated|public class" org/testcontainers/containers/PostgreSQLContainer.java
  #  ->  @deprecated use org.testcontainers.postgresql.PostgreSQLContainer instead.
  ```
  Follow the `@deprecated use ... instead` pointer. Do not ship the shim.
- Docs pages usually name the successor too — useful as a hint, but confirm the
  replacement symbol in the jar before typing it.
- If your IDE/inspection flags a deprecation warning, treat it as a blocker to fix
  now, not a "later".

## The recurring garbage list

Symbols that Boot-3 / older-stack habit gets wrong on this project. These were all
verified against Boot 4.1 / Modulith 2.1 / the current toolchain — verify before you
type, do not trust memory:

| You might type (from habit)                            | Reality on this project                                                        |
|--------------------------------------------------------|--------------------------------------------------------------------------------|
| `import ...boot.autoconfigure.domain.EntityScan`       | Boot 4 moved it → `org.springframework.boot.persistence.autoconfigure.EntityScan` |
| `spring-boot-starter-web`                              | Boot 4 web-MVC starter is `spring-boot-starter-webmvc`                          |
| `spring-boot-starter-test` (one umbrella)             | Boot 4 split it — use per-slice: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway-test` |
| `ApplicationModules.of(SomePlainClass.class)`          | throws `IllegalArgumentException` — the type must be `@Modulithic`, `@Modulith`, or `@SpringBootApplication` |
| bootVersion `4.1.0.RELEASE` in an Initializr URL       | Boot 4 dropped `.RELEASE`; the resolvable version is `4.1.0`                    |
| `flyway` core dep only                                 | Boot 4 has `spring-boot-starter-flyway`; for Postgres also add `org.flywaydb:flyway-database-postgresql` |
| a `tailwind.config.js` + `@tailwind base;` directives  | Tailwind v4 is config-less here: `@import "tailwindcss";` in CSS + `@tailwindcss/vite` plugin |
| a `"pnpm": {...}` block in `web/package.json`          | pnpm 11 no longer reads it; build settings live in `pnpm-workspace.yaml`/`.npmrc` |
| `org.testcontainers.containers.PostgreSQLContainer` (and its `<?>` generic form) | `@Deprecated` in Testcontainers 2.x → `org.testcontainers.postgresql.PostgreSQLContainer` (non-generic, no type param) |
| `@MockBean` / `@SpyBean`                                | removed → `@MockitoBean` / `@MockitoSpyBean`                                    |
| Spring AI starter / vector-store bean names            | version-sensitive — confirm the exact `spring-ai-*` artifact against the jar / Maven Central before adding |
| `SearchRequest.query("...").withTopK(5)` (Spring AI 1.0-M style, still in stale Javadoc) | 2.0 is builder-based: `SearchRequest.builder().query("...").topK(5).build()` — verify in the jar |

When you hit a NEW moved/renamed symbol, add a row here — this table is the
project's memory of the traps, and it pays for itself the next time.

## Cost vs benefit

A verification check takes seconds; a failed `compileJava` cycle costs a
build + error-log parse, and a symbol that compiles but fails at boot (a moved
package that resolves to nothing, a starter that pulls the wrong module) can cost a
whole run. The break-even is one prevented failure per session. Read the jar/`.d.ts`
(or copy a known-good call site); use Context7 only to find patterns worth
verifying; never ship an unverified symbol.

For the per-file static check AFTER you have written code, see the
`northstar-static-analysis` skill (Gate 1). This skill is the earlier, cheaper
defense: it stops the bad symbol from being typed in the first place.
