---
name: northstar-create-test
description: Create or update tests for the Northstar monorepo — backend (Spring Boot 4.1 + Spring Modulith + JPA/Flyway in core/ and apps/*) and frontend (React 19 + TypeScript + Vite in web/). Use this whenever you add or change behavior that needs a test: domain logic, a repository/JPA mapping, a Modulith module or its events, a REST controller, a React component or hook, a Spring AI capture/embedding/RAG
path, or a critical user flow. Encodes the 2026 test stack — JUnit 5 + Testcontainers + @ApplicationModuleTest on the backend, Vitest + React Testing Library + MSW + Playwright on the frontend — and the traps specific to this stack (real Postgres not H2, the @Transactional rollback nuance, mocking the API at the network boundary). Read before writing any test here.
---

# Create a test (Northstar)

Pick the smallest test that actually proves the behavior, then write it against
the real boundary — a real Postgres for persistence, the real network layer for the
web. A test that passes by mocking away the thing that breaks is worse than no test:
it turns green and lies.

Before typing any unfamiliar Spring / Modulith / Testcontainers / Vitest / MSW
symbol you did not copy from this repo, confirm it — see `northstar-verify-api-symbol`.
Test APIs move across majors (JUnit 4→5, MSW 1→2, `@MockBean`→`@MockitoBean`); a
guessed symbol compiles-then-fails or silently no-ops.

## Choose the smallest test type

| Behavior under test | Test type |
|---|---|
| Pure domain logic (wiki-link parser, SM-2 scheduler, requirement-gap calc) | plain JUnit / Vitest unit — no Spring, no render |
| Repository query or JPA↔schema mapping | `@DataJpaTest` + Testcontainers Postgres |
| One Modulith module + its events | `@ApplicationModuleTest` (+ `Scenario`) |
| REST controller (request→response, validation, status) | `@WebMvcTest` + MockMvc |
| Cross-module flow / full context load (Gate 2) | `@SpringBootTest` + `@ServiceConnection` |
| Spring AI extraction/RAG wiring (does the code work) | `@SpringBootTest` + mocked `ChatModel`/`EmbeddingModel` |
| AI output quality (is the answer good) | evaluation test — gated, not in CI |
| React component / hook behavior | Vitest + React Testing Library (+ MSW) |
| A whole user flow in a real browser | Playwright `@playwright/test` |

Most logic belongs at the top of that table (fast, no infrastructure). Reserve
`@SpringBootTest` and Playwright for what genuinely needs the whole stack — they are
the slow tip of the pyramid.

---

# Backend

## Real Postgres, never H2

Northstar's schema uses Postgres-only features — `text[]` (discipline.ikigai),
`pg_trgm` indexes, `tsvector` search, and pgvector later. H2 does not implement
these; an H2-backed test passes on SQL that Postgres would reject (and vice-versa),
so it proves nothing about production. Use **Testcontainers** with the same Postgres
image as `compose.yaml` (`pgvector/pgvector:pg17`). The 2026 idiom is
`@ServiceConnection` — Boot wires the datasource from the container automatically, no
manual `spring.datasource.*`:

```java
@DataJpaTest
@Testcontainers
class NoteRepositoryTests {

    @Container
    @ServiceConnection                         // org.testcontainers.postgresql.PostgreSQLContainer
    static PostgreSQLContainer postgres =      // Testcontainers 2.x: non-generic; NOT org.testcontainers.containers.* (deprecated)
            new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired NoteRepository notes;

    @Test
    void resolvesBySlug() {
        var now = Instant.now();
        notes.save(new Note(UUID.randomUUID(), "Gradle recovery", "gradle-recovery", "body", now));

        assertThat(notes.findBySlug("gradle-recovery")).isPresent();
    }
}
```

`@DataJpaTest` also runs Flyway against the container, so a JPA field with no
matching column fails here — this is the test that catches the `ddl-auto: validate`
mismatch class of defect.

## Modulith module test — the Northstar-native pattern

`@ApplicationModuleTest` bootstraps ONE module (from `com.northstar.core.*`) with
only its declared dependencies, not the whole app. It is how you test a module in
isolation and, with the `Scenario` / `PublishedEvents` API, how you test the
event-driven seams between modules (capture → task, note change → worker embed):

```java
@ApplicationModuleTest                       // bootstraps just this module
class CaptureModuleTests {

    @Autowired CaptureService capture;

    @Test
    void publishesTaskExtractedEvent(Scenario scenario) {
        scenario.stimulate(() -> capture.ingest("nộp đơn Thanh Hoá 15/7"))
                .andWaitForEventOfType(TaskExtracted.class)
                .matchingMappedValue(TaskExtracted::title, "nộp đơn Thanh Hoá")
                .toArrive();
    }

    // or assert on what was published, without waiting:
    @Test
    void extractsExpense(PublishedEvents events) {
        capture.ingest("ăn sáng 30k");
        assertThat(events.ofType(ExpenseExtracted.class)).hasSize(1);
    }
}
```

Prefer this over `@SpringBootTest` for module logic: it is faster and it proves the
module's dependencies are actually sufficient (a hidden reach into another module
fails to wire here). Pair with the `ApplicationModules.verify()` test in
`northstar-static-analysis` — that one guards structure, this one guards behavior.

## Controller slice

```java
@WebMvcTest(NoteController.class)
class NoteControllerTests {

    @Autowired MockMvc mvc;
    @MockitoBean NoteService noteService;   // NOT @MockBean (removed)

    @Test
    void returns404ForMissingNote() throws Exception {
        given(noteService.findById(any())).willReturn(Optional.empty());
        mvc.perform(get("/api/notes/{id}", UUID.randomUUID()))
           .andExpect(status().isNotFound());
    }
}
```

## AI / LLM code (Spring AI)

Test Spring AI code (capture extraction, embeddings, search, MCP tools) by
splitting one question into two:

**(a) Does the code work? — deterministic, mocked, runs in CI.** Never call a real
model or embedding API in a normal test: it is slow, costs money, and is
non-deterministic, so it makes the suite flaky and expensive. Mock the model bean and
return a canned response, which tests your wiring + your parsing of the model's
structured output — the parts that actually break:

```java
@SpringBootTest
class CaptureExtractionTests {

    @MockitoBean ChatModel chatModel;          // no real LLM call
    @Autowired CaptureService capture;

    @Test
    void mapsExpenseFromModelJson() {
        // make the model return exactly the structured output your prompt asks for
        given(chatModel.call(any(Prompt.class))).willReturn(cannedJson(
            "{\"type\":\"EXPENSE\",\"amount\":30000,\"category\":\"food\"}"));

        assertThat(capture.extract("ăn sáng 30k"))
            .singleElement().isInstanceOf(ExpenseDraft.class);
    }
}
```

Better still: keep the prompt-building and the structured-output parsing as pure
functions so most of them need no Spring and no mock at all. (The exact
`ChatResponse`/`Generation` shape is version-sensitive — confirm it via
`northstar-verify-api-symbol` before typing.)

For the vector store, use Testcontainers **pgvector** with a **mocked
`EmbeddingModel`** that returns fixed vectors — you test the similarity-search wiring
without an embedding API and without non-determinism:

```java
@Testcontainers
class SemanticSearchTests {
    @Container @ServiceConnection
    static PostgreSQLContainer pg = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @MockitoBean EmbeddingModel embeddingModel;   // deterministic vectors
    @Autowired VectorStore vectorStore;
    // seed docs, search, assert ordering — no LLM involved
}
```

**(b) Is the AI output good? — evaluation, gated, NOT in default CI.** Whether the
model's answer is actually relevant/grounded is a separate, non-deterministic
question. Spring AI ships evaluators for it — `RelevancyEvaluator` (is the answer
relevant to the retrieved context) and `FactCheckingEvaluator` (is the claim grounded
in the document). These call a real model, so tag them and gate them behind an env
var; run them nightly or on demand, never as a blocking CI gate:

```java
@Test
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "LLM_TESTS", matches = "true")
void ragAnswerIsRelevant() {
    var evaluator = new RelevancyEvaluator(ChatClient.builder(chatModel));
    // build EvaluationRequest{question, retrieved context, answer}; assert isPass()
}
```

Keep (a) and (b) apart: (a) proves the code is correct and stays green
deterministically; (b) measures quality and is allowed to be flaky and slow.

## The @Transactional nuance (Spring ≠ Jmix habit)

`@DataJpaTest` is transactional and rolls back each test by default — convenient and
fine for repository/mapping tests. But do NOT put `@Transactional` on tests that
exercise **committed** behavior: Modulith event publication, `@Async` listeners,
DB triggers, or flush/commit-time constraints all depend on a real commit, and a
rolling-back test hides them (worse, event tests deadlock or see nothing). For those,
run non-transactional against Testcontainers and clean up explicitly, or give each
test class a fresh container. Rule of thumb: rollback for "did the query work",
committed for "did the event/side-effect happen".

## Run the smallest command

```bash
./gradlew :core:test --tests "com.northstar.core.note.NoteRepositoryTests"
./gradlew :core:test --tests "com.northstar.core.capture.*"
```

---

# Frontend

## Vitest, not Jest

The web is Vite-native, so the test runner is **Vitest** (shares the Vite config,
no separate Babel/Jest transform). Unit-test pure functions and hook logic directly:

```ts
import { expect, test } from "vitest";
import { parseWikiLinks } from "@/features/notes/parseWikiLinks";

test("extracts wiki link targets", () => {
  expect(parseWikiLinks("see [[Gradle recovery]] and [[Jmix]]"))
    .toEqual(["Gradle recovery", "Jmix"]);
});
```

## Components: React Testing Library, test behavior not markup

Query the way a user perceives the UI — by role/label/text, not by CSS class or a
test id. A test coupled to markup breaks on every refactor and proves nothing about
behavior; a test coupled to roles survives refactors and asserts what the user sees.

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

test("confirms a captured entity", async () => {
  render(<CaptureReview entities={[expenseCard]} />);
  await userEvent.click(screen.getByRole("button", { name: /confirm all/i }));
  expect(screen.getByText(/đã lưu/i)).toBeInTheDocument();
});
```

Query priority: `getByRole` > `getByLabelText` > `getByText` > (last resort)
`getByTestId`. Reach for `findBy*` (async) over arbitrary waits.

## Mock the API at the network boundary with MSW

Do NOT stub `fetch` or the `openapi-fetch` client — mock the actual HTTP with **MSW**
(Mock Service Worker). It intercepts at the network layer, so the component runs its
real client code, and you can type the mock responses from the generated OpenAPI
types so a backend contract change surfaces in the mocks too:

```ts
import { http, HttpResponse } from "msw";        // MSW 2.x API
import { setupServer } from "msw/node";

const server = setupServer(
  http.get("/api/notes/:id", () =>
    HttpResponse.json({ id: "…", title: "Gradle recovery", contentMarkdown: "…" })),
);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

`onUnhandledRequest: "error"` is deliberate — an un-mocked call is a bug in the test,
not something to silently pass through.

## Playwright for real flows only

Reserve browser E2E for what only a real browser proves — routing, auth, a full
capture→note→backlink walk. Keep it to critical paths; everything a component or
unit test can prove should not be a slow Playwright test.

```ts
import { test, expect } from "@playwright/test";

test("capture creates a note", async ({ page }) => {
  await page.goto("/inbox");
  await page.getByPlaceholder(/viết một câu/i).fill("học 30p IELTS");
  await page.getByRole("button", { name: /capture/i }).click();
  await expect(page.getByText(/studylog/i)).toBeVisible();
});
```

## Run

```bash
pnpm -C web test                     # vitest (watch off in CI: vitest run)
pnpm -C web test parseWikiLinks      # a single file
pnpm -C web exec playwright test     # E2E (needs api + web running)
```

Test deps are not installed yet; add them when you write the first test:
`vitest @testing-library/react @testing-library/user-event @testing-library/jest-dom
jsdom msw` (dev) and `@playwright/test`.

---

## Audit before you finish

- The assertion checks a **result** (a value, a persisted row, a visible element),
  never merely "it did not throw".
- Persistence tests use unique data and clean up committed rows — or roll back where
  that is valid (see the @Transactional nuance). Run the class **twice back-to-back**;
  a second green run proves no leaked rows or cross-test coupling.
- Frontend tests fail on unhandled requests (`onUnhandledRequest: "error"`) and query
  by role/text, not test ids or markup.
- The command can run one class/file, not only the whole suite.

## Forbidden / smells

- `@MockBean` — removed; use `@MockitoBean`.
- H2 (or any in-memory DB) for persistence tests — Northstar's schema is Postgres-specific.
- `@Transactional` on a test of committed behavior (events, async, triggers) — it rolls the effect back and the test sees nothing.
- `new Note(...)`-style asserts that never touch the DB, when the point is the mapping/query.
- Stubbing `fetch`/the api client instead of MSW — you then test your stub, not the client.
- `getByTestId` / class selectors when a role or label query exists.
- Hardcoded `sleep`/`setTimeout` waits — use `findBy*`, `waitFor`, Playwright auto-wait, or the Modulith `Scenario` await.
- A Playwright test for what a component or unit test already proves.
- Calling a real LLM / embedding API in a default-CI test — mock the model for correctness; gate the quality-evaluation tests behind an env var.
