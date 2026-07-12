# Capability Model Catalogs Plan

## Backend

- [x] Persist Chat, TTS, Web Search, Web Fetch, STT, Image, and Embedding catalogs independently.
- [x] Return capability catalogs in gateway descriptors and connection tests.
- [x] Merge manual and discovered targets with deterministic fallback.
- [x] Persist route options and apply configured TTS language during synthesis.
- [x] Cover 9Router capability discovery, voice metadata, and empty catalogs.

## Web

- [x] Split gateway manual catalogs by capability behind a compact advanced section.
- [x] Show capability-specific connection-test counts.
- [x] Reuse the AI Elements searchable Model Selector in every AI route row.
- [x] Add searchable TTS voice plus language selection and filtering.
- [x] Share provider model marks with Assistant.

## Verification

- [x] Regenerate OpenAPI and web client.
- [x] Run backend and web gates.
- [x] Verify OpenAI and 9Router Settings at desktop and mobile widths.
- [x] Consolidate durable docs and move this increment to completed.
