# 0022 - AI Gateways Declare Supported Capabilities

## Status

Accepted on 2026-07-12. Supersedes the single-protocol-type portion of
[0021](./0021-runtime-ai-gateways-are-protocol-instances.md).

## Decision

Gateway instances retain one encrypted credential and base URL, but declare one
of three contracts:

- `OPENAI`: chat plus OpenAI web-search, speech, and Realtime protocols;
- `NINE_ROUTER`: chat plus 9Router search, fetch, and batch speech protocols;
- `OPENAI_CHAT_COMPATIBLE`: the conservative `/models` and chat contract only.

Feature routes reference an existing gateway and a model, provider alias, or
combo target. Capability adapters own endpoint-specific schemas. A connection
test remains in AI Settings; feature settings never ask for the same URL or
credential again.

The supported-capability set describes protocols Northstar has deliberately
bound to that gateway type. It does not claim that every endpoint exposed by an
upstream server is implemented.

## Consequences

- OpenAI can reuse one credential for chat and `/responses` web search.
- 9Router can reuse one credential for chat, `/search`, and `/web/fetch` while
  keeping its upstream providers, combos, quota, and fallback in 9Router.
- A custom OpenAI-chat-compatible server is not called with unverified search,
  fetch, audio, or Realtime schemas.
- New capability adapters can be added when a Northstar workflow consumes them;
  a general plugin or request-mapping framework is not required.
