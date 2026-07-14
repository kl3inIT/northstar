# Assistant Common File Attachments Design

Status: active.

Date: 2026-07-14

## Outcome

The web Assistant composer accepts ordinary safe documents as well as raster
images. Images remain direct multimodal model input. Documents are stored in
the immutable Attachment vault, extracted and embedded by the worker, and read
only through attachment-scoped indexed excerpts. The user can ask about a file
in the same submitted turn once its preparation reaches `READY`.

This increment covers uploaded local files. It does not turn the public web
reader into a PDF fetch proxy, add archives/video, or publish document reading
through the public MCP server.

## Research

- The Attachment vault already stores immutable SHA-256-deduplicated files with
  a 25 MiB cap and sandboxed downloads.
- `SearchService` already uses Spring AI 2.0 `TikaDocumentReader`,
  `TokenTextSplitter`, and pgvector, while the worker polls the idempotent index
  every 20 seconds. The missing boundary is Composer access to that index.
- Spring AI 2.0's `TikaDocumentReader` returns one flattened `Document` with
  only `source` metadata. It cannot support truthful page citations.
- Spring AI 2.0's `PagePdfDocumentReader` defaults to one `Document` per page
  and records the exact `page_number`, optional `end_page_number`, and
  `file_name` metadata. It is therefore used only for PDFs; Tika remains the
  broad reader for Office and other supported formats.
- The current chat endpoint interprets every `attachmentId` as an image and
  rejects documents. The native AI Elements attachment components already
  render non-image file tiles, so no parallel upload widget is needed.

## Settled decisions

### D1 - One composer, two delivery paths

Raster PNG, JPEG, GIF, and WebP files are sent as Spring AI `Media`, subject to
the existing 8 MiB chat-image cap. Prepared documents are never sent as media
or Base64. Their bounded indexed excerpts are added to the per-turn system
context as untrusted evidence.

### D2 - The accepted document surface is an explicit allowlist

The composer accepts PDF; TXT/Markdown; DOC/DOCX/RTF; PPT/PPTX; CSV/XLS/XLSX;
HTML; JSON/XML; and ordinary source-code text extensions. Archives,
executables, disk images, macro-enabled Office documents, arbitrary binary,
audio, and video are rejected. Audio retains its explicit speech/transcription
paths. Claimed MIME is not authoritative: raster images require magic bytes,
and a filename/type policy normalizes document types before storage.

### D3 - Preparation is worker-owned and observable

Tika/PDF parsing, chunking, and embeddings stay in `apps/worker`. A search-owned
attachment-index state records `PENDING`, `PROCESSING`, `READY`, `FAILED`, or
`UNSUPPORTED`, the current index hash, and a safe error code. Missing or stale
state is `PENDING`. The API exposes a batch read-only status endpoint; the web
polls it after upload and does not dispatch the chat turn until every document
is `READY`. Failed files remain in the composer for retry/removal.

### D4 - Retrieval is scoped to the submitted attachment ids

The chat endpoint validates every id and partitions images from documents. It
rejects a document that is not ready. `SearchService` retrieves only chunks
whose `attachmentId` is in the current request, uses the user question for
semantic selection when the document exceeds the context budget, and includes
small documents completely. No unrelated note or file may enter this context.

### D5 - Document text is untrusted and bounded

Extracted text is evidence, never instructions. The system prompt wraps it in
an explicit untrusted-document section and instructs the model to ignore any
commands inside. A fixed total character budget and per-file chunk ceiling
bound model context and provider cost. The worker owns the CPU/memory-heavy
parsers; API requests read derived text only.

### D6 - Citations use only provenance the reader actually preserves

Every excerpt carries its attachment URL, safe filename, chunk number, and MIME
type. PDF chunks also carry exact page numbers from
`PagePdfDocumentReader`. Assistant answers cite the stored file link and page
where available. Office, spreadsheet, HTML, and source files use filename plus
chunk/section provenance unless a future structured reader provides truthful
slide/sheet/line metadata. Northstar never invents locators after Tika flattened
the source.

### D7 - Existing durability and idempotency rules remain

Upload deduplication remains content-addressed. The single-flight composer lock
includes upload and preparation polling, and the existing durable
`Idempotency-Key` is claimed only when the prepared turn reaches the chat API.
User-message history stores durable file parts/links, not extracted context or
raw bytes. Structured document sources are emitted in the live stream; inline
Markdown citations and user attachment tiles remain available after reload.

### D8 - The public MCP and web-reader boundaries do not expand

This is an authenticated in-app Assistant capability. It does not add a paid or
private file-reading tool to MCP and does not change Decision 0012's rule for
public URL readers. Existing `search_knowledge` may still find previously
indexed files globally; direct Composer retrieval is a stricter per-turn path.

## Failure behavior

- Unsupported type or mismatched raster bytes fail before storage with a safe,
  actionable `400` response.
- A password-protected, malformed, empty-text, or parser-failing document
  becomes `FAILED`/`UNSUPPORTED` with no raw parser/provider details returned.
- Worker or embedding unavailability leaves the document pending/failed and the
  composer intact; the API never falls back to parsing synchronously.
- A stale or forged attachment id is rejected before the model or tools run.
- If retrieval finds no derived chunks despite `READY`, the turn fails closed
  instead of asking the model to guess about the file.

## Gates and verification

1. Type-policy tests cover accepted common files, raster magic checks, and
   rejected archive/executable/audio/video/macro/binary inputs.
2. Search tests cover PDF page metadata, index-state transitions, safe failure,
   stale-hash behavior, scoped retrieval, context bounds, and no cross-file
   leakage.
3. Assistant integration tests cover mixed image/document turns, not-ready
   rejection, injected untrusted context, and emitted file source parts.
4. Web unit/type tests cover generic upload, preparation polling, retained
   failed drafts, attachment labels, and single dispatch.
5. A real Chromium flow uploads at least one text document, observes
   preparation, asks a question, and opens the resulting file citation.
6. Consolidate the Assistant/knowledge specs and test matrices, add a new
   decision, update the roadmap, move this increment to `completed/`, and append
   the verified behavior to the Northstar App Behavior note.
