# 0038 — Assistant documents use worker-owned scoped ETL

Status: accepted.

## Context

Northstar already stored immutable attachments and used Spring AI's
`TikaDocumentReader`, `TokenTextSplitter`, and pgvector for derived knowledge
indexing. The web Assistant, however, accepted only images and interpreted every
attachment id as multimodal media. Sending document bytes/Base64 directly to a
model would bypass the existing storage/index lifecycle, inflate prompts, lose
truthful provenance, and let uploaded content behave like instructions.

Spring AI's ETL model separates a `DocumentReader`, optional
`DocumentTransformer`, and `DocumentWriter`. Its page PDF reader preserves page
metadata, while Tika intentionally gives broad flattened extraction rather than
format-specific slide/sheet/line structure.

## Decision

- The composer accepts an explicit allowlist of safe common files. Server-side
  magic/type inspection is authoritative; archives, executables, macro-enabled
  Office files, audio/video, and arbitrary binary remain outside this path.
- Raster images continue as direct multimodal `Media`. Documents remain durable
  Attachment bytes and are never inserted into prompts as Base64.
- Parsing, splitting, and embedding remain worker-owned. A search-owned state
  exposes `PENDING`, `PROCESSING`, `READY`, `FAILED`, and `UNSUPPORTED`; the API
  never runs Tika or PDF parsing on a chat request thread.
- PDFs use `PagePdfDocumentReader` and preserve its real page metadata. Other
  binary documents use `TikaDocumentReader`; plain text is decoded directly.
  Northstar does not invent locators discarded by a flattened reader.
- A chat turn waits for every submitted document to become ready and retrieves
  only bounded derived chunks whose attachment ids are in that request. The
  user question guides semantic selection for large documents; small documents
  can be included completely within the fixed budget.
- Derived document text is wrapped as untrusted evidence in the per-turn system
  prompt. Raw extracted context is not persisted in conversation history, while
  durable attachment links and structured source parts remain replayable.
- Existing content-hash idempotency and the Assistant's durable turn-level
  idempotency claim remain the retry boundaries.

## Consequences

One composer now handles ordinary files without creating a second chat or a
provider-specific document API. Heavy parsers and embedding cost stay out of
the latency-sensitive API, attachment scoping prevents unrelated knowledge from
leaking into a turn, and PDF citations are page-truthful. A document cannot be
used immediately when the worker or embedding route is unavailable; the UI
shows preparation/failure and retains the draft instead of silently degrading.
Public URL/PDF reading and MCP file ingestion remain separate future decisions.
