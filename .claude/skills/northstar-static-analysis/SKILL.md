---
name: northstar-static-analysis
description: Gate-1 static checks for the Northstar monorepo (Spring Boot 4.1 + Spring Modulith + JPA/Flyway backend in core/ and apps/*, plus a Vite + React + TypeScript web in web/). Run this on every file you created or edited before moving on. PRIMARY when a JetBrains IDE inspection (e.g. get_file_problems) is connected: run it per file — it catches the Spring/Modulith/JPA and TS defects a compiler cannot (unresolved @Value/property keys, missing beans, invalid JPA queries, unresolved TS imports). Fall back to ./gradlew compileJava, the Modulith verification test, tsc, and mechanical checks when no inspection is available. Read this before relying on any static check on Java, application.yml, Flyway SQL, or TypeScript files.
---

# Static analysis (Gate 1)

Gate 1 = every file you created or edited passes a static check before you move
on. **Run the JetBrains IDE inspection first when it is connected; fall back to
compile + the Modulith verify test + mechanical checks when it is not.**

Why a gate at all: the Northstar stack has whole classes of defects that compile
perfectly clean and only blow up when the app boots or renders — a Modulith
boundary violation, a JPA entity that no longer matches its Flyway migration
(`ddl-auto: validate`), a duplicated key in `application.yml`, a stale generated
OpenAPI client. `compileJava` and the bundler are blind to all of these. Gate 1 is
where you catch them cheaply, per file, before they cost you a boot cycle.

## 1. JetBrains IDE inspection — PRIMARY when connected

If a Jmix/Spring-aware IDE inspection (JetBrains `get_file_problems`) is
connected, run it PER FILE on every `.java`, `application.yml`, Flyway `.sql`, and
`.ts`/`.tsx` you touched — this is your primary Gate-1 check. With IntelliJ
Ultimate's Spring + database + TypeScript support it is the only STATIC catch for
defects the compiler cannot see: unresolved `@Value`/`${...}` property keys,
missing/ambiguous beans, invalid `@Query` JPQL, JPA fields with no matching column,
and unresolved TS imports or type errors the bundler tolerates. Rules when you use
one:

- **Always pass `projectPath` — the absolute repo root (`pwd`, i.e. the directory
  containing `settings.gradle.kts`).** Pass it on EVERY JetBrains MCP call
  (`get_file_problems`, `get_project_modules`, `create_new_file`, …): with more than
  one project open the IDE cannot tell which you mean and the call fails — `No exact
  project is specified` or a malformed `-32602` — so the gate silently does nothing.
  **If you do not know the root:** issue any JetBrains MCP call once WITHOUT
  `projectPath`; the error lists every open project as `Currently open projects:
  {"projects":[{"path": …}]}`. Pick the entry equal to (or the deepest one
  containing) your `pwd`, then reuse it as `projectPath` for all later calls.
- **Surface WARNINGS, not just errors.** Spring/JPA/unresolved-reference findings
  are typically reported as WARNINGS, not errors — an errors-only view looks clean
  when it is not. Include warnings and treat unresolved-reference / Spring-inspection
  warnings as blockers.
- **Never trust an EMPTY result you did not confirm.** An inspection that silently
  targeted the wrong module returns "no problems" on a file it never looked at — that
  is false-clean. Confirm the file was actually inspected before calling it clean.
- **The inspection needs Northstar opened as its own Gradle project** (root =
  `settings.gradle.kts` directory), not as a subdirectory of another open project — a
  nested path resolves to the wrong module and returns generic noise (e.g. "URI is
  not registered") instead of real findings. Even when it works it can miss things,
  so keep the compile + Modulith + mechanical checks alongside it.
- **Fallback decision rule.** If the inspection returns "URI is not registered" or
  only generic/non-Spring findings on a file you KNOW is a real source file you just
  edited, treat the inspection as UNAVAILABLE for this run — do NOT call the file
  clean. Fall through to the compile + Modulith + mechanical checks below.

## 2. Compile — the ground truth, and the floor when no inspection

Java (`core/`, `apps/*`):

```bash
./gradlew --no-daemon compileJava
```

Authoritative for `.java`: unresolved symbols, wrong imports/packages, type
mismatches, bad handler/bean-method signatures. Cheap; run it regardless — it is
the precondition for Gate 2.

TypeScript (`web/`):

```bash
pnpm -C web typecheck        # tsc --noEmit
```

Authoritative for `.ts`/`.tsx`: type errors and unresolved imports that Vite's
esbuild transform silently skips (esbuild strips types, it does not check them).

But **both compilers are BLIND to configuration and cross-cutting wiring.** A
`application.yml` with a duplicated `spring:` key, a JPA entity whose column no
longer exists in any Flyway migration, an app that forgets to scan
`com.northstar.core`, a Modulith module reaching into another module's internals,
or a `web/src/lib/api.gen.d.ts` that is stale versus the backend — all compile
clean and fail at boot/render. A clean compile proves NOTHING about any of those.
A 0-byte `.java`/`.yml`/`.ts` also compiles clean — confirm written files are
non-empty.

## 3. Modulith verification — the semantic check unique to Northstar

Module boundaries are the Northstar equivalent of a descriptor contract: they
compile clean and only fail the architecture test.

```bash
./gradlew --no-daemon :core:test
```

`ModulithVerificationTests` runs `ApplicationModules.verify()`. It fails when a
module in `com.northstar.core.*` reaches into another module's internals instead of
its public API or an event — the exact coupling that quietly turns a modular
monolith into a big ball of mud. Run this whenever you add or edit anything under
`core/src/main/java/com/northstar/core/`. Cross-module access should go through a
module's published types or Modulith events, never a peer module's package-private
class.

## 4. Mechanical checks — the floor when no inspection

When you have no semantic inspection, these are your static floor for the
boot/render-time defect classes. Run from the repo root. The `find` checks are
pass/fail (any output = a defect to fix); the `grep` checks SURFACE candidates —
a non-empty result is not automatically a failure, but you MUST explain every hit.

```bash
# package line on every new .java (missing -> scan/registry breakage).
# grep -L lists files with NO package line anywhere, so a leading Javadoc/license
# comment (e.g. package-info.java) is not a false positive.
find core apps -name '*.java' -path '*/src/*' -exec grep -L '^package ' {} +

# no 0-byte source/config file (empty class breaks scan; empty migration/yml misconfigures at boot)
find core apps -path '*/src/*' -type f \( -name '*.java' -o -name '*.yml' -o -name '*.sql' \) -size 0

# each app that touches JPA must scan com.northstar.core (else "Not a managed type" at boot)
grep -rL "com.northstar.core" apps/*/src/main/java --include='Northstar*Application.java'

# Flyway migrations must be named V<n>__<desc>.sql and live under core resources
find core/src/main/resources/db/migration -type f ! -name 'V*__*.sql' 2>/dev/null

# @Entity / @Column fields to reconcile against a Flyway migration (ddl-auto=validate
# fails at boot if a mapped column has no matching DDL). Explain each new/changed one.
grep -rn "@Column\|@Table\|@Entity" core/src/main/java --include='*.java'

# duplicated top-level key in an application.yml silently loses config or fails boot
for y in $(find apps -name 'application*.yml'); do awk '/^[a-zA-Z]/{k=$1; if(seen[k]++) print FILENAME": duplicate top-level key "k}' "$y"; done
```

For the web, the OpenAPI client is generated — a stale one drifts from the backend:

```bash
# if the api changed, regenerate before trusting web types
pnpm -C web gen:api        # rewrites web/src/lib/api.gen.d.ts from contracts/openapi.json
pnpm -C web typecheck      # stale/renamed fields now surface as type errors
```

These mechanical checks are MANDATORY whenever you have neither an inspection nor
a Gate-2 run — they are then your ONLY static catch for those defect classes.

## Per-file loop

For each file you created or edited: inspection-if-connected (else the relevant
compile) → fix every error and every unresolved-reference / Spring-inspection
warning → repeat until clean. Run it on EVERY file, not a sample — the defects that
survive are the ones you were confident about and never checked.

After the last edit, the preconditions for Gate 2 are a full `./gradlew
--no-daemon build` (compile + `:core:test` Modulith verify) and `pnpm -C web
build`. Config/wiring defects that only surface at runtime (JPA↔schema `validate`,
bean wiring, `application.yml`) are truly confirmed only by **Gate 2** — a
Testcontainers `@DataJpaTest` / `@ApplicationModuleTest` / `./gradlew test` (write
these with the `northstar-create-test` skill) — and **Gate 3**, actually booting the
app (`./gradlew :apps:api:bootRun`, or drive the web via the `/run` and `/verify`
skills). Gate 1 narrows the search; it does not replace the run.

For verifying an unfamiliar Spring / Spring Modulith / library API symbol BEFORE
you type it (the cheapest defense against a hallucinated method or moved package —
recall `@EntityScan` moved to `org.springframework.boot.persistence.autoconfigure`
in Boot 4), see the `northstar-verify-api-symbol` skill — it is the earlier gate
that stops the bad symbol being typed in the first place.
