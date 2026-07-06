# Spring AI Playbook (distilled from official guides)

Distilled from the Spring AI reference "Guides" section, filtered for what Northstar can
actually use. Skips what we already do (XML-sectioned prompts, contrastive few-shots,
length anchors, self-check, `""`-instead-of-omit). Model note: we run OpenAI **gpt-5.5,
a reasoning model** — `temperature`/`topK`/`topP` do not apply; the sampling-knob advice
below is only relevant if we ever add a non-reasoning model (e.g. a cheap judge model).

---

## 1. Prompt engineering patterns

Source: <https://docs.spring.io/spring-ai/reference/api/chat/prompt-engineering-patterns.html>

| Pattern | What it is | Northstar relevance |
|---|---|---|
| Zero-shot | Task instruction, no examples. Baseline before adding examples. | Fine for trivial one-off calls; our main surfaces already need few-shot. |
| One/Few-shot | 3–5 examples teach format + edge cases. | Already core to `CaptureService`. Rule of thumb worth adopting elsewhere: reach for few-shot the moment output *format* matters. |
| System prompting | Persistent behavioral frame separate from user turns via `.system(...)`. | Already used by assistant chat. Keep instructions in system, data in user — Spring AI's split maps 1:1 to our XML sections. |
| Role prompting | Persona ("act as a study advisor") shapes tone/depth. | **Not used yet.** Candidate: alignment review commentary (`AlignmentService`) — a "candid weekly-review coach" role gives consistent voice; future study module tutor persona. |
| Contextual prompting | Inject situational params via `PromptTemplate` params (`.param("context", ...)`). | We do this ad hoc; the useful bit is Spring AI's `u -> u.text(tpl).param(...)` instead of string concat — safer templating for `AlignmentService` fact blocks. |
| Step-back prompting | Two calls: first extract principles/background, then solve using them. | **Not used yet.** Candidate: alignment review — call 1 "what themes/risks stand out in these facts?", call 2 writes commentary grounded in that. Also study-plan generation (principles of the curriculum → plan). |
| Chain of Thought | "Let's think step by step" / worked examples. | Mostly moot: gpt-5.5 reasons natively. Do **not** add CoT scaffolding to its prompts; only relevant for a small non-reasoning judge model. |
| Self-consistency | Run N times at high temperature, majority-vote. | Reframed for us: run the capture classifier N times and vote when confidence matters (eval harness, not prod — cost/latency). Useful to stabilize eval baselines. |
| Tree of Thoughts | Generate k candidates → evaluate → expand best. | Overkill for current surfaces. Possible future: study-schedule generation with alternatives scored against constraints. |
| Automatic Prompt Engineering | LLM generates N prompt variants, another call scores them, keep the best. | **Not used yet, cheap win**: use it offline to iterate the CaptureService prompt against the pinned test set instead of hand-tweaking. |
| Code prompting | Low-temperature code write/explain/translate. | Not a product surface for us. |
| Structured output / `.entity()` | `.call().entity(MyRecord.class)` — records/enums instead of parsing JSON strings. | We use structured output; the checklist item: every new AI call returns a record via `.entity()`, never `content()` + manual parse. Enum fields (e.g. capture kind) give free validation. |
| `ChatOptions` (temperature/topK/topP/maxTokens) | Portable sampling + length control. | Only `maxTokens` applies to gpt-5.5. Set it deliberately per surface (short for classification, generous for review drafts) — it is a hard length anchor that backs up our prose length anchors. |
| Provider-specific options | `OpenAiChatOptions` — `seed`, `responseFormat`, reasoning effort etc. | Use `OpenAiChatOptions` (not portable `ChatOptions`) where we need OpenAI-only knobs; `seed` for reproducible eval runs. |

Guide's own best-practice list, filtered: combine techniques (system + few-shot +
structured output — we already do); use self-consistency for high-stakes decisions;
prefer entity mapping everywhere.

**Top adoption candidates:** step-back for `AlignmentService`, APE offline for the
capture prompt, role prompting for review voice, `seed` + self-consistency in evals.

---

## 2. Agent workflow patterns (Building Effective Agents)

Source: <https://docs.spring.io/spring-ai/reference/api/effective-agents.html>
(Anthropic's patterns ported to Spring AI; runnable code in
`spring-ai-examples/agentic-patterns`.)

Core distinction: **workflows** = LLM calls orchestrated by *your* code (predictable);
**agents** = LLM directs its own tool loop (adaptive). Guide's stance: prefer the
simplest workflow that works; agents only when steps can't be predicted. Northstar
already has one true agent (assistant chat with ~27 tools) — everything else should
stay a workflow.

### Chain workflow
Sequential calls, each output feeds the next prompt. Use when steps are inherently
ordered and you trade latency for accuracy.
- **Northstar candidate:** alignment review drafting as an explicit 2–3 step chain
  (facts → themes (step-back) → commentary → tighten-to-length) instead of one mega-prompt.

### Routing workflow
A cheap classification call picks which specialized system prompt / handler processes
the input. Use when inputs fall into distinct categories needing different treatment.
- **Northstar candidate:** capture classification *is* a router. If capture prompts grow
  (task vs note vs finance vs habit each needing different extraction), split into
  route-then-specialized-extract instead of one prompt handling every kind — smaller
  prompts, independently test-pinned.

### Parallelization workflow
Fan out independent LLM calls (sectioning or voting), aggregate in code.
- **Northstar candidate:** weekly review — draft each discipline's section in parallel,
  then merge; voting variant = self-consistency for capture eval.

### Orchestrator-workers
An LLM orchestrator decomposes a task into unpredictable subtasks; workers execute;
code combines. Use only when subtasks genuinely can't be enumerated upfront.
- **Northstar candidate:** weekly/monthly review at scale: orchestrator decides which
  disciplines/projects deserve deep dives this week, workers draft each. Don't reach
  for this until the parallelization version proves insufficient.

### Evaluator-optimizer
Loop: generate → LLM evaluates against criteria → regenerate with feedback → repeat
until pass or max attempts. Use when clear evaluation criteria exist and iteration
measurably helps.
- **Northstar candidate:** review-draft quality gate — evaluator checks the commentary
  against a rubric (grounded in the supplied facts only? actionable? within length?)
  and one retry with feedback. Also capture-draft quality before showing the user.
  Spring AI ships this as an advisor — see §3 (`SelfRefineEvaluationAdvisor`).

Principles worth pinning: start simple, compose patterns (router → chain → evaluator is
a legitimate pipeline), let the LLM make the routing/decomposition/evaluation decisions
but keep control flow in Java, and use `.entity()` for every inter-step handoff so the
pipeline is typed.

---

## 3. LLM-as-a-Judge

Source: <https://docs.spring.io/spring-ai/reference/guides/llm-as-judge.html>

Two judge patterns:
- **Direct assessment (point-wise):** judge scores one output on a small integer scale
  (guide uses 1–4) + written feedback. Best for quality gates and regression evals.
- **Pairwise comparison:** judge picks the better of two candidates. Best for A/B-ing
  prompt changes (old capture prompt vs new one on the same inputs).

Spring AI's building block: **`SelfRefineEvaluationAdvisor`** (experimental, recursive
advisors, non-streaming only). It wraps a ChatClient call: evaluate the response with a
*separate* judge ChatClient → if rating < threshold, re-ask with the judge's feedback
appended → up to `maxRepeatAttempts`. Judge returns a structured record
`(int rating, String evaluation, String feedback)` via `.entity()`.

```java
ChatClient.builder(openAi)
    .defaultAdvisors(SelfRefineEvaluationAdvisor.builder()
        .chatClientBuilder(ChatClient.builder(judgeModel)) // separate model = less self-preference
        .successRating(3).maxRepeatAttempts(1).build())
```

Note: **non-streaming only** — usable for `CaptureService` and `AlignmentService`
(blocking calls), *not* for the SSE assistant chat path.

### How Northstar would eval with a judge

**Capture classification** — mostly does *not* need a judge: kind/fields are exact-match
against the pinned test set (cheaper, deterministic). Judge earns its keep on the fuzzy
part: is the extracted title/summary faithful to the raw capture? Rubric sketch (1–4):
1. wrong kind or invented facts; 2. right kind, title misses the point; 3. correct and
faithful, title clunky; 4. correct, faithful, title is what the user would have typed.

**Alignment review quality** — the flagship judge use case, since there's no ground
truth. Direct-assessment rubric (1–4) with explicit criteria: (a) every claim traceable
to a supplied fact — no invention; (b) actionable, not platitudes; (c) respects the
length anchor; (d) candid tone, not cheerleading. Run point-wise per draft in an eval
suite; run pairwise (old prompt vs new prompt, same week's facts) when changing the
review prompt.

### Pitfalls and mitigations (from the guide)

| Bias | Mitigation |
|---|---|
| Self-preference (model grades its own output kindly) | Different model as judge than generator |
| Position bias (pairwise: first/last answer favored) | Randomize order; run both orders and require agreement |
| Verbosity bias (longer = scored higher) | Length-neutral rubric criteria; our length anchor doubles as a scored criterion |
| Hallucinated/jittery grading | temperature 0 / deterministic judge settings; integer scale, never free-form 0–100 |
| Prompt brittleness | Few-shot examples of each rating level in the judge prompt (fits our contrastive-example convention) |

Guardrails: cap retry loops (cost is N× per call), keep human review for high-stakes
outputs, and prefer a small dedicated judge model over the big generator model.

---

## 4. Dynamic tool search

Source: <https://docs.spring.io/spring-ai/reference/guides/dynamic-tool-search.html>

Instead of loading all tool definitions into every request, the model gets one search
tool and pulls in relevant definitions on demand via a `ToolIndex` (semantic/Lucene/
regex), through `ToolSearchToolCallingAdvisor` — 34–64% token reduction and better tool
selection at 30+ tools. Already ours: wired in
`apps/api/src/main/java/com/northstar/api/assistant/AssistantConfig.java` for the
~27-tool assistant; this guide adds nothing beyond what's implemented.
