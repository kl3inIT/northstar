# Finance Tracking V1 — Plan

Execute top to bottom; each step compiles/tests before the next.

## 1. core.finance module + V21 migration

- `V21__finance.sql`: `finance_transaction` table — BaseEntity columns
  (id/created_at/updated_at/version), `type VARCHAR(16)` CHECK EXPENSE/INCOME,
  `amount BIGINT NOT NULL CHECK (amount > 0)`, `occurred_on DATE NOT NULL`,
  `description VARCHAR(255) NOT NULL`, `category VARCHAR(64) NOT NULL`,
  `exceptional BOOLEAN NOT NULL DEFAULT FALSE`, `source VARCHAR(16)` CHECK
  CAPTURE/ASSISTANT/MANUAL. Index on `occurred_on`.
- `core/finance/`: `package-info.java`, `Transaction` (extends BaseEntity),
  `TransactionType`, `TransactionSource`, package-private
  `TransactionRepository` (month range query, distinct categories, aggregate
  sums), public `FinanceService` (record/update/delete/find, `month(YearMonth)`
  list, `monthSummary` — totals + per-category + exceptional aggregate + prev
  month comparison, `categories()` = seed ∪ used), `TransactionSummary` +
  `MonthSummary`/`CategoryTotal` records, `TransactionNotFoundException`.
- `:core:test`: service round-trip + summary math + category union (follow the
  existing per-module test style).

## 2. Capture EXPENSE kind

- `ExpenseDraft(List<Item> items)`, Item = type/amount(long)/description/
  category/occurredOn(String ISO)/exceptional.
- `CaptureDraft`: add `EXPENSE` kind + `expense` field (nullable like others).
- `CaptureService` prompt: classification rubric addition (money already
  moved → EXPENSE; intention to buy → TASK), `<expense_shape>` section with VN
  money grammar contrastive examples, category vocabulary block fed from
  `FinanceService.categories()`, exceptional rubric (routine vs one-off),
  multi-item instruction. CaptureService gains a FinanceService dependency
  (wired in CaptureConfig).
- Receipt: `CaptureService.draftFromImage(byte[] image, String mimeType)` —
  same system prompt, user message carries the image via ChatClient media;
  forced kind EXPENSE.

## 3. Assistant/MCP FinanceTools + review

- `core/assistant/FinanceTools`: `log_transactions`, `spending_summary`,
  `find_transactions`, `delete_transaction` — dual `@Tool`+`@McpTool`,
  NorthstarTool marker, hints (readOnly for reads, destructive for delete).
  Prompts/descriptions follow prompting-best-practices (explicit "" for
  absent, ISO dates).
- `draft_review` weekly: spending block = ordinary category totals +
  exceptional aggregate + typical-week reference (median of prior 4 full
  weeks), skipped entirely when the ledger is empty.
- Add read/upsert/delete tools for monthly category budgets and read/create/
  update/contribute/delete tools for savings goals. Writes only run on explicit
  user intent; read tools expose progress for reviews and planning.

## 3.5 Monthly budgets + savings goals + recurring definitions (V22)

- `V22__finance_planning.sql`: `finance_budget` unique by month/category and
  `finance_savings_goal` plus `finance_subscription`, all with BaseEntity
  audit/version columns and positive amount constraints.
- `Budget`/`BudgetSummary` and `SavingsGoal`/`SavingsGoalSummary` entities,
  repositories, not-found exceptions, and FinanceService CRUD/contribution
  methods. Budget spent/remaining/progress are derived from transaction rows.
- PostgreSQL integration tests cover uniqueness/upsert, over-budget math, goal
  edit/contribution/delete, subscription payment/advance, and schema validation.

## 4. apps/api + contract

- `apps/api/finance/`: `FinanceController` — GET `/api/finance?month=yyyy-MM`
  (transactions), GET `/api/finance/summary?month=`, POST `/api/finance`
  (list of items — the capture confirm path), PATCH `/api/finance/{id}`
  (category/exceptional/description/amount/occurredOn), DELETE. Request
  records with validation; register not-found in ApiExceptionHandler.
- `CaptureController`: POST `/api/capture/receipt` (multipart image →
  CaptureDraft) + CaptureRequest unchanged (text path already generic).
- Finance planning endpoints: monthly budget list/upsert/update/delete and
  savings-goal list/create/update/contribute/delete.
- Regenerate: bootRun with auth off → curl `/v3/api-docs` →
  `contracts/openapi.json` → `pnpm -C web gen:api`.

## 5. Web

- `pnpm -C web add @tanstack/react-table`; `shadcn add table` primitive if
  not present.
- `web/src/lib/finance-api.ts`: hand-written hooks (list/summary/log/patch/
  delete + invalidation), tzHeaders where relevant.
- Router: `/finance` route; app-shell: enable Finance nav item.
- `web/src/pages/finance.tsx`: Transactions and Budgets & goals tabs. Rework the
  ledger into the compact stat strip + search/category/type toolbar + dense
  table pattern from `shadcn-fintech`. Build real SVG category budget rings,
  budget CRUD, savings-goal progress cards, goal CRUD, and contributions.
  There is still NO transaction capture input here.
- Capture page: render EXPENSE drafts (items echo + save) like task/event
  chips, and add the receipt-photo upload next to text/voice input.

## 6. Gates + consolidation

- Gate 1: `./gradlew --no-daemon compileJava`, `pnpm -C web typecheck`,
  `./gradlew :core:test`. Gate 3: render proof of `/finance`.
- Consolidate: `docs/specs/domains/finance.md` (new), capture spec update,
  assistant-and-mcp spec update, test matrix, decision record (narrow budgets /
  constrained categories / TanStack-not-Kibo for tables), roadmap, move
  increment to completed.

## Completion

Completed on 2026-07-10.

- Gate 1: Java compile/core tests, generated-client typecheck, web lint/build,
  and diff checks passed.
- Gate 2: `./gradlew --no-daemon clean test` passed across all deployables.
- Gate 3: Playwright exercised Finance at desktop/mobile sizes, including
  budget/goal create, edit, contribution, and delete; `/capture` retained the
  expense, receipt, and dictation entry controls; console errors were zero.
- Targeted PostgreSQL tests cover canonical categories, duplicate budget
  conflicts, savings-goal optimistic versioning, and subscription payment.
- Streamable HTTP MCP tests cover finance tool discovery plus budget and
  savings-goal write/read round trips.
