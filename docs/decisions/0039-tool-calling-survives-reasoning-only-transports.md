# 0039 - Tool Calling Survives Reasoning-Only Transports

Date: 2026-07-30

## Status

Accepted.

## Context

Selecting OpenAI's `gpt-5.6-terra` directly in Chat killed the Assistant. Every
turn failed with a provider 400: function tools are not supported together with
`reasoning_effort` on `/v1/chat/completions`, and the provider's remedy is either
`/v1/responses` or `reasoning_effort: none`. Spring AI 2.0's OpenAI model speaks
only chat completions, and every Northstar turn attaches tools, so the model was
unusable for chat while still being offered by the picker.

The failure spread past the one conversation: a new conversation inherits the
most recently used Assistant selection, so every new chat inherited the rejected
model. The client also showed nothing — the stream's single fixed `error` frame
was never rendered — so a model that could not serve this chat looked exactly
like a broken deployment.

Options considered:

- Restrict the picker to models known to accept tools while reasoning. Rejected:
  gateway catalogs are runtime data, so a name list goes stale the next time a
  provider ships a model, and it silently removes models the user paid for.
- Send `reasoning_effort: none` for every model whose id looks like a reasoning
  tier. Rejected for the same reason, plus it degrades models that accept both
  and breaks models that reject the parameter outright.
- Implement a `/v1/responses` transport so reasoning and tools can coexist.
  Right, but a real increment rather than a fix; it stays on the roadmap.
- Retry the rejected call with reasoning disabled, keyed off the provider's own
  rejection. Chosen.

## Decision

A chat call rejected for combining function tools with reasoning is retried once
with `reasoning_effort: none`, and the model id is remembered so later turns
apply it up front instead of paying a rejected round trip. Detection matches the
provider's wording, not a model-name allowlist.

The retry lives in a `ChatModel` decorator in the OpenAI-compatible integration,
not in the `ChatClient`. `MessageChatMemoryAdvisor` writes the user message to
chat memory before the model is called and the tool-calling loop runs above the
model, so retrying higher would duplicate the memory entry and could re-run tool
calls whose side effects already committed. A call that has already produced
output is never replayed.

A failed turn also emits one actionable `error` frame. The delivery layer reads
only the HTTP status off the failure and returns a fixed sentence per class of
status, so provider bodies cannot echo credentials or prompt fragments into the
browser, and the web chat surfaces that sentence.

## Consequences

An affected model answers with reasoning off rather than not at all; the trade is
explicit and provider-driven. The catalog stays open — any model the gateway
lists remains selectable, and models that accept tools while reasoning are
untouched because nothing rejects them. One rejected call per model per API
process remains visible in logs as Spring AI's aggregation error before the
retry succeeds.

A `/v1/responses` transport would remove the trade-off entirely and supersedes
this decision when it lands.
