# Vocabulary Production And Rich Enrichment — Design

## Goal

Extend focused vocabulary review with optional production practice, useful
word-formation explanations, and opt-in mnemonic images. Expensive enrichment
runs without blocking the review queue and is still previewed before any card
or file is persisted.

## Decisions

### D1 — One vocabulary item owns one or two independent memory states

The existing vocabulary row remains the single content source. Recognition
(`target expression -> meaning`) is always enabled. Production
(`sense-specific meaning -> target expression`) is optional and carries its
own Ebisu state. Enabling production never copies front/back/metadata.

Each deck has a default for newly-created items; an item can override it.
Existing items remain recognition-only after migration. The focused queue
selects at most one direction for an item per session snapshot, choosing its
weaker enabled direction, so sibling directions never teach each other in the
same session.

### D2 — Production prompts are sense-specific, not blind reversal

Recognition accepts a meaning-equivalent answer in Vietnamese or English.
Production asks for the target expression from the saved meaning, part of
speech, and optional context. It does not pronounce or otherwise reveal the
target before answer reveal. Answer checking is direction-aware; only the
learner's Again/Hard/Good/Easy rating updates the corresponding memory state.

### D3 — Word formation is optional and may be absent

`WORD_FORMATION` enrichment returns a structured explanation only when a
useful, defensible decomposition exists: prefix/root-or-base/suffix parts,
one plain-language composition, and a short word family. `re-` in `reassign`
is a prefix, not mislabeled as a root. When the model is uncertain or a modern
decomposition would mislead, it returns no word-formation value. False
etymology is rejected.

### D4 — Images route through the provider-neutral image capability

`IMAGE` enrichment resolves `AiTask.IMAGE_GENERATION`. The core contract
contains only prompt, bytes, and media type; OpenAI/Nine Router request types
remain in `integrations/ai-openai-compatible`. Direct OpenAI and Nine Router
both use their OpenAI-compatible `/images/generations` endpoint. Nine Router
targets come from `/models/image`; combos are valid targets and own any
provider fallback.

The generated illustration contains no text and is designed as a mnemonic
cue for the saved sense. A preview carries base64 bytes in transient memory.
Only Apply stores validated raster bytes through `AttachmentService` and adds
`frontImageId` plus non-answer-leaking alt text to card metadata. Discard
removes the transient result and writes nothing.

### D5 — Enrichment is an in-session background job

Starting enrichment returns a job id immediately and closes the Sheet. A
single process-local, expiring job cache holds status and preview; generation
runs on the application task executor. The learner can continue reviewing and
receives a toast when the original item is ready. A persistent review-header
action shows running/ready state until Apply or Discard, so losing the toast
never loses access to the preview. Opening either action shows the preview at
the top of the Sheet rather than below the selection list.

Jobs are deliberately non-durable: restart or expiration loses an unapplied
preview, never card data. Apply is server-side so image storage and metadata
update occur in one transaction boundary as far as the Study workflow is
concerned; failures never report success. The first UI supports one active job
per browser review session.

## Metadata

```json
{
  "reading": "/riːəˈsaɪn/",
  "partOfSpeech": "verb",
  "frontImageId": "6d7189d8-43df-46fc-b335-5877289336ab",
  "frontImageAlt": "Mnemonic illustration for this vocabulary card",
  "wordFormation": {
    "parts": [
      { "form": "re-", "kind": "prefix", "meaning": "again" },
      { "form": "assign", "kind": "base", "meaning": "give a task" }
    ],
    "explanation": "reassign means assign again",
    "family": ["assign", "assignment", "reassignment"]
  }
}
```

## Verification

1. Migration keeps every existing item recognition-only and preserves review history.
2. Unit tests pin independent direction updates, one-sibling queue selection,
   deck defaults, morphology omission, metadata preservation, and image response validation.
3. Adapter tests pin direct OpenAI and Nine Router request/response shapes.
4. API tests pin background status, preview-only generation, Apply, Discard, and failure.
5. Web tests cover direction prompts, production toggle, front image, background toast,
   preview-at-top, and no request before explicit Generate.
6. Local Java/Web gates and a live browser pass complete before consolidation.

## Completion evidence

- Java compile, Spring Modulith/core tests, image-adapter tests, API job/controller
  tests, web typecheck/tests/build, Flyway V43 clean-schema boot, and Hibernate
  validation are green locally.
- A real browser pass verified deck defaults, recognition followed by production
  on a later snapshot, hidden production listening, reversible Space, background
  review continuation, preview-at-top, explicit Apply, and a front-only image.
- Direct OpenAI `gpt-image-2` generated and persisted one live mnemonic image.
  A live `WORD_FORMATION` response that was not defensibly decomposable degraded
  to null without failing or removing the image. Applying after a concurrent
  review succeeded; actual content changes remain conflict-protected.
- Decision 0026, Study spec/test matrix, roadmap, and the Northstar App Behavior
  note are updated.
