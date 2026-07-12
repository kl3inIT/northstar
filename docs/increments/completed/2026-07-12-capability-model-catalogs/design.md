# Capability Model Catalogs Design

## Problem

AI gateway Settings previously treated every manually configured identifier as
a chat model. That is inaccurate for multi-capability gateways such as 9Router,
whose Chat, TTS, STT, Image, Embedding, Web Search, and Web Fetch catalogs have
different discovery endpoints and may independently be empty.

## Decision

- Keep one gateway credential and connection per provider.
- Store manual identifiers in a separate catalog for each capability.
- Discover 9Router targets through `/models`, `/models/tts`, `/models/stt`,
  `/models/image`, `/models/embedding`, and the typed entries of `/models/web`.
- Treat discovery as enrichment: a missing or failed endpoint never erases a
  configured manual catalog or fails an otherwise valid gateway test.
- Enrich TTS targets from `/audio/voices` when available; persist the selected
  language as route metadata and use it when synthesis omits a locale.
- A gateway connection test reports every capability independently.
- Runtime route rows reuse the same searchable AI Elements Model Selector and
  provider marks as Assistant.
- Missing discovered targets never erase a manual catalog.

## Compatibility

Existing `models` data remains the chat catalog. New nullable-safe catalog
columns default to empty strings, while route metadata defaults to an empty
JSON object, so existing gateway and route rows remain valid.
