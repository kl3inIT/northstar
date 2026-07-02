---
name: northstar-verify-api-symbol
description: Before typing a class, annotation, package path, starter/artifact coordinate, method, or npm export you have not personally verified in the Northstar project, confirm it exists. Costs seconds and prevents the most expensive class of failures — hallucinated or moved symbols (e.g. Boot 4 relocated @EntityScan and renamed starters vs Boot 3 muscle memory) that survive typing and then break compile or app boot. Use this whenever you are about to write an unfamiliar Spring / Spring Modulith / JPA / Flyway import or a web (TanStack/openapi-fetch/Tailwind) API you have not already seen in this repo's source. Primary check = the Context7 docs MCP; floor = grep the repo for a working call site or read the dependency's own types.
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

## How to verify — Context7 first, floor always

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

1. **Context7 — PRIMARY.** It is connected here and the project's standing rule is
   to use it for any library/framework symbol. `resolve-library-id` for the library
   (e.g. "Spring Boot", "Spring Modulith", "TanStack Router", "Tailwind CSS"), then
   `query-docs` for the exact symbol/shape — the current package of an annotation,
   the correct starter name, the method signature, the export style. This resolves
   against the version's real docs, so it catches "moved in the new major" traps a
   from-memory guess will not.

2. **Read the dependency's own types — if Context7 is unavailable or ambiguous.**
   - Backend: the symbol lives in a jar in the Gradle cache; confirm the class and
     its package before importing:
     ```bash
     for j in $(find "$HOME/.gradle/caches/modules-2" -name 'spring-boot*.jar'); do \
       unzip -l "$j" 2>/dev/null | grep -q "EntityScan.class" && echo "$j" && \
       unzip -l "$j" | grep "EntityScan.class"; done
     ```
   - Web: `node_modules/<pkg>/dist/*.d.ts` is the authoritative export list.

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

- **Context7 / the docs** — prefer the version's current guidance; if the page marks
  something deprecated it names the successor.
- **The jar's own sources** are the ground truth for `@Deprecated` — the Javadoc spells
  out the replacement:
  ```bash
  # unzip the *-sources.jar from the Gradle cache and read the class header
  grep -iE "deprecated|public class" org/testcontainers/containers/PostgreSQLContainer.java
  #  ->  @deprecated use org.testcontainers.postgresql.PostgreSQLContainer instead.
  ```
  Follow the `@deprecated use ... instead` pointer. Do not ship the shim.
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
| Spring AI starter / vector-store bean names            | version-sensitive — confirm the exact `spring-ai-*` artifact via Context7 before adding |

When you hit a NEW moved/renamed symbol, add a row here — this table is the
project's memory of the traps, and it pays for itself the next time.

## Cost vs benefit

A verification check takes seconds; a failed `compileJava` cycle costs a
build + error-log parse, and a symbol that compiles but fails at boot (a moved
package that resolves to nothing, a starter that pulls the wrong module) can cost a
whole run. The break-even is one prevented failure per session. Verify via Context7
if you can, else grep a known-good call site or read the jar/`.d.ts`; never ship an
unverified symbol.

For the per-file static check AFTER you have written code, see the
`northstar-static-analysis` skill (Gate 1). This skill is the earlier, cheaper
defense: it stops the bad symbol from being typed in the first place.
