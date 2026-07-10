# Finance Tracking V1 — Design

## Intent

A focused, AI-native personal finance module: transactions start as natural
language (typed text, voice dictation, or a receipt photo) and the LLM does the
structuring — amounts, dates, categories, ordinary-vs-exceptional. Finance also
owns two explicit planning primitives the user requires: monthly category
budgets and savings goals. There are still no wallets/accounts or transfers.
Insight arrives through the Finance page and the AI-drafted weekly review.

## Research grounding (2026-07-10)

Three research sweeps (popular apps incl. Money Lover/MISA/1Money/YNAB/Monarch;
AI-native apps incl. Copilot Money/Cleo/Gullak/Telegram bots; academic PFM +
HCI literature) converged on these constraints — they are requirements, not
suggestions:

1. **Entry friction budget: ~5s, no follow-up question** in the common case
   (benchmark: 1Money's 2-tap grid). Multi-item messages ("nay tiêu: sáng 30k,
   cafe 45k, grab 62k") must produce multiple transactions from one send —
   this replaces the daily ritual the user explicitly cannot sustain.
2. **The draft echo is the behavioral intervention** (Huebner et al. 2020:
   a small semantic act per transaction + weekly feedback reduced spending;
   silent auto-categorization forfeits the effect). The user must see amount +
   category before saving — the existing capture review flow already does this.
3. **Categories: constrained vocabulary, not free text** (Cheema & Soman 2006:
   flexible categorization is a self-licensing loophole; drift like
   cafe/coffee/đồ uống breaks week-over-week comparison). Seed a VN-tuned
   consensus taxonomy; the LLM must reuse existing values and create new ones
   reluctantly.
4. **`exceptional` flag is first-class** (Sussman & Alter 2012: one-off
   purchases are where budgets structurally fail; Huebner: feedback only works
   when it separates ordinary vs exceptional and aggregates the exceptional).
5. **Budget maintenance must stay narrow and AI-assisted**. Research warns that
   people stop adjusting elaborate budgets after breaches, but the user has
   explicitly identified category budgets and savings goals as necessary.
   Northstar therefore supports only monthly category caps and goal progress,
   exposes them to assistant/MCP tools, and avoids accounts, envelopes,
   automatic recurring posting, or budget hierarchies.
6. **LLM extracts; deterministic code resolves** (ANNA bank case study,
   Gullak): dates anchored to today in the user zone inside the prompt, output
   as ISO strings, amounts in VND longs; small batches; server generates ids.
7. Lapses are normal (Epstein): batch catch-up entry works by design
   (multi-item + "hôm qua" dates); no streaks, no guilt, descriptive tone.

## Domain model

The ledger entity is `core.finance.Transaction` (table
`finance_transaction`, V21):

- `type` — `EXPENSE | INCOME`
- `amount` — `bigint`, VND (no decimals)
- `occurredOn` — `date` (when the money moved, not when recorded)
- `description` — short free text ("ăn sáng bún bò")
- `category` — text from the constrained vocabulary
- `exceptional` — boolean, AI-suggested, user-overridable; one-off atypical
  purchase vs routine spending
- `source` — `CAPTURE | ASSISTANT | MANUAL` (entry channel, for later analysis)

V22 adds two planning entities and one narrow recurring definition:

- `Budget` — one positive VND limit for a category in one `YearMonth`; spent,
  remaining, and percentage are derived from ledger rows, never duplicated.
- `SavingsGoal` — name, positive target, current saved amount, optional target
  date, and optional expected monthly contribution. Contributions increment the
  saved amount without pretending to be expenses or transfers.
- `Subscription` — a monthly/yearly charge definition with next due date and
  active state. It writes an expense and advances only when explicitly marked
  paid; it is available through REST/assistant/MCP but is not a Finance UI tab.

Deliberately absent: wallets/accounts, automatic recurring posting, transfers,
and multi-currency (VND only; foreign amounts can be captured as VND by the
user).

### Category vocabulary

Seeded expense categories (consensus of Money Lover/MISA/1Money/Mint/Monarch,
VN-tuned): Ăn uống, Cafe, Đi lại, Hóa đơn, Nhà cửa, Mua sắm, Sức khỏe,
Giải trí, Học tập, Du lịch, Hiếu hỉ, Gia đình, Khác. Income: Lương, Thưởng,
Quà tặng, Khác. Cafe and Hiếu hỉ are deliberately top-level (VN spending
reality; MISA/1Money precedent). The prompt receives seed list ∪ categories
already used in the ledger; "Khác" is the mandated sink when unsure.

## Entry paths (all three feed one extractor)

1. **Capture text/voice**: `CaptureDraft` gains kind `EXPENSE` with
   `ExpenseDraft(items[])`; each item = amount (long, VND), description,
   category, occurredOn (ISO string), exceptional, type (EXPENSE/INCOME).
   Rubric: text stating money already spent/received → EXPENSE (vs an
   intention to buy → TASK). VN money grammar in the prompt with contrastive
   examples: `35k`→35000, `2tr`→2000000, `2tr5`→2500000, bare number = VND.
2. **Receipt photo**: new capture endpoint takes an image, sends it through
   the same ChatClient (multimodal) with the same expense shape — merchant
   line items become `items[]`. The image is not stored (voice-transcription
   precedent: media in, structure out).
3. **Assistant/MCP tools** (`core.assistant.FinanceTools`, dual
   `@Tool`+`@McpTool`): `log_transactions` (accepts a list — the model does
   the parsing in-chat), `spending_summary` (month totals by category,
   ordinary vs exceptional, vs previous month), `find_transactions`,
   `delete_transaction` (destructive hint), plus budget/goal list and upsert
   tools so the assistant can maintain planning data on explicit user intent.

## Read paths

- **`/finance` page** (replaces the disabled Wallet placeholder): Transactions
  and Budgets & goals tabs. Transactions uses a compact stat strip, search/type/
  category filters, and a dense shadcn Data Table over TanStack. Planning uses
  animated SVG budget rings and savings-goal progress rows inspired by the MIT
  `shadcn-fintech` reference, backed by Northstar data and forms. Capture — text,
  voice and receipt photo — lives only on the Capture page.
- **Weekly review** (`draft_review`): spending section following the only
  experimentally supported feedback formula — ordinary category totals AND
  aggregated exceptional purchases with an AI-inferred typical-week/month
  reference. Descriptive tone, never scolding.

## Consequences / trade-offs

- No reconciliation ground truth: silent under-logging is invisible. Accepted
  for V1; V2 candidate is a chat "balance check-in" comparing stated bank
  balance vs ledger delta.
- Category corrections are not yet fed back as few-shot exemplars (Copilot
  pattern); V1 relies on vocabulary constraint + existing-values-in-context.
  The correction data (user edits category) is captured in the ledger itself,
  so the loop can be added later without schema change.
- Duplicate detection is out of V1 (personal volume is low); soft-flag on
  same-amount-same-day is a V2 candidate.
- Savings-goal balances are user/assistant-reported because V1 has no accounts.
  A contribution updates goal progress and does not affect expense totals.
