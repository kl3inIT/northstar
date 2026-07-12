# 0026 - Vocabulary Items Own Optional Production Memory

## Status

Accepted.

## Context

Recognition (word to meaning) and production (meaning to word) are different
recall tasks. Copying one vocabulary item into two cards would duplicate its
content, enrichment, and correction path. Treating both directions as one
memory state would overestimate the weaker direction. Rich AI enrichment also
takes long enough that a synchronous panel interrupts focused review.

## Decision

- One vocabulary item remains the only content record. Recognition is always
  enabled; production is optional and has an independent Ebisu model and review
  log direction.
- Deck settings choose the default only for new items. An item can override the
  default, and migration leaves existing items recognition-only.
- A review queue snapshot includes at most one direction for each item: its
  weakest enabled direction. The reverse prompt therefore cannot immediately
  teach its sibling in the same session.
- Production uses the saved, sense-specific meaning as its prompt and does not
  reveal or pronounce the target before the answer is shown.
- Word formation is best-effort structured enrichment. An uncertain or invalid
  decomposition is omitted instead of failing the job or inventing etymology.
- Image generation uses the provider-neutral `IMAGE_GENERATION` route. Direct
  OpenAI and Nine Router protocol details stay in the integration adapter.
- Enrichment previews are short-lived, process-local API jobs. Review may
  continue and advance the entity version while a job runs; Apply rejects only
  actual content changes. Image bytes are persisted through Attachments only on
  explicit Apply. Closing, discarding, expiry, or API restart stores nothing.

## Consequences

Recognition and production can decay independently without duplicating notes,
and deck selection remains flat and predictable. Background jobs deliberately
do not use the worker or survive restart; durable, multi-device enrichment would
require a later persisted-job design. The API returns preview image bytes while
ready, so jobs are single-user, short-lived, size-limited, and expire after 30
minutes.
