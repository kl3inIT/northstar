# Speech Assessment — Plan

Execute in four blocks; each block ships compilable and gated
(compileJava + compileTestJava + :core:test + web typecheck). Read design.md
fully first — it carries the session decisions and the Azure specifics.

Environment prerequisites (already done, verify before starting):
`AZURE_SPEECH_KEY` + `AZURE_SPEECH_REGION=eastasia` in repo-root `.env`;
sanity check the key with
`curl -X POST https://eastasia.api.cognitive.microsoft.com/sts/v1.0/issueToken -H "Ocp-Apim-Subscription-Key: $KEY" -H "Content-Length: 0"`
→ expect 200. Add both vars to `.env.example` and `docker/env.server.example`
with placeholders.

## Block A — Contract + Azure adapter (no UI)

1. `core.study`: `SpeechAssessor` interface + result records per design.
   Public types; no Azure imports in core.
2. New Gradle module `integrations/speech-azure` (copy the
   `integrations/web-openai` build/module conventions): `AzureSpeechAssessor`
   implementing the contract over the short-audio REST endpoint with the
   `Pronunciation-Assessment` base64 header — VERIFY exact request/response
   field names against the official how-to page before writing the parser
   (design D7 has the sketch and the doc link). JDK HttpClient; 30s timeout;
   loud typed errors (401 → "key/region mismatch", 429 → "quota", body
   excerpt in the exception).
3. `apps/api`: config class binding `northstar.speech.azure.key/region`
   (yml maps from `AZURE_SPEECH_KEY`/`AZURE_SPEECH_REGION`, empty defaults);
   bean only when key non-blank; endpoints 503 via ObjectProvider absence.
4. Tests: adapter param-encoding test (mock HTTP), locale/CJK helper,
   response-parsing test against a captured real response JSON (run one live
   call on the dev machine, save the JSON as a test resource, strip nothing).

## Block B — Speaking domain

1. Migration `V32__speaking.sql` (check current max — automation/brief
   workstreams also add migrations; renumber to next free), per design.
2. `SpeakingFeedback` entity + repository + summary record +
   NotFoundException (mirror the Writing* set), `SpeakingService`
   (list/find/delete/save/recentForCorpus).
3. `SpeakingCoach` (not a component): assess → LLM content feedback with the
   corpus injected → structural validation + faithfulness evaluator with one
   corrective retry → persist → auto-log Speaking study session. Wire in
   apps/api with ChatClient + SpeechAssessor + pinned
   `northstar.study.grader-model`.
4. `WritingService.grammarWeaknesses()` → union writing + speaking topErrors
   (extend WritingServiceTests to cover the union and label collisions
   across sources).
5. Tools: extend the study toolset — `list_speaking_feedback`,
   `delete_speaking_feedback` (dual @Tool/@McpTool, mirror WritingTools);
   update `grammar_weaknesses` description to say it now includes spoken
   errors. Assistant system-prompt routing line for "luyện nói" (points at
   the Speaking tab).

## Block C — API + Web

1. Endpoints per design (pronunciation on vocab card, speaking question,
   speaking attempt, speaking history GET/DELETE); ApiExceptionHandler
   additions; contract regen + `pnpm -C web gen:api`.
2. Web: WAV recorder utility (AudioWorklet capture → 16 kHz mono downsample
   → WAV encode, 60s cap); Vocabulary tab mic dialog; new Speaking tab
   (stats, question generator with part selector, recorder, result card,
   history + detail dialog + delete). Follow the existing tab component
   patterns in `web/src/features/study/`.
3. Playwright verification per design gate 5; screenshots.

## Block D — E2E + consolidation

1. Live E2E on dev machine: real recorded WAV through both flows; verify a
   speaking attempt appears in the Speaking tab, logs a Speaking session on
   the Log tab, and its errors surface in `grammar_weaknesses` output.
2. Consolidate per design gate 6 (spec, tests matrix, decision doc, roadmap,
   move increment, App Behavior note via MCP).

Guardrails: do not touch other in-flight workstreams' uncommitted files; do
not commit root artifacts (*.png); never log or echo the Azure key; commits
in logical chunks with conventional messages per docs/conventions.md.
