# 9Router Capability Routing

## Problem

Runtime AI gateways currently route only model-backed chat workloads. Northstar's
web research still calls OpenAI and Firecrawl directly even when the same server
already exposes 9Router's normalized `/v1/search` and `/v1/web/fetch` endpoints.

An OpenAI-compatible chat connection does not imply that every media endpoint
shares the chat schema. The connection credential can be reused, but each
capability still needs its own provider-neutral port and protocol adapter.

## Decision

- Persist `OPENAI`, `NINE_ROUTER`, and conservative
  `OPENAI_CHAT_COMPATIBLE` gateway contracts with explicit supported
  capabilities.
- Expose a server-only gateway connection resolver from the AI integration. It
  returns the resolved base URL, credential, and timeout; secrets never cross an
  HTTP boundary.
- Make web-provider routing carry an optional `{gatewayId, target}` pair. Direct
  OpenAI/Firecrawl providers ignore it. The 9Router adapter requires it.
- Persist search and page-reader routes with the existing web-research singleton
  setting. The target is a 9Router provider alias or combo name.
- Rework OpenAI web search to reuse an `OPENAI` gateway rather than a duplicate
  web-search credential.
- Implement one 9Router adapter for both Northstar web ports:
  `POST {gateway}/search` and `POST {gateway}/web/fetch`.
- Keep Northstar's existing cache, fallback, URL safety checks, citations, and
  assistant tools unchanged above the provider boundary.

## Scope

In scope:

- runtime selection of a configured gateway and 9Router search/fetch target;
- normalized response parsing and failure mapping;
- Settings UI, OpenAPI contract, migration, tests, and browser verification.

Out of scope:

- replacing OpenAI Realtime dictation (9Router's batch STT endpoint is a
  different protocol);
- replacing Azure pronunciation assessment (generic STT has no pronunciation
  scoring contract);
- adding text-to-speech before Northstar has a product workflow that consumes
  generated speech.

The gateway resolver and capability-specific adapter shape intentionally leave
room for batch transcription and TTS without coupling them to chat routing.
