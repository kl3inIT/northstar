# AI Gateway And Streaming

## Goal

Make Assistant streaming conform to the Vercel AI SDK UI Message Stream v1
contract and route every model-backed Northstar task through configurable
OpenAI-compatible gateways. OpenAI direct, 9Router, OpenRouter, LiteLLM, and
similar services are gateway instances of the same protocol, not hard-coded
provider types.

## Decisions

- Keep Spring MVC. Return a reactive SSE body from the Assistant endpoint;
  Spring adapts the `Flux` through servlet async processing.
- Preserve Vercel's wire contract: exact UI-message parts, `[DONE]`, required
  headers, Nginx buffering suppression, and comment heartbeats during quiet
  model/tool periods.
- Keep upstream and downstream protocols separate. A gateway emits
  OpenAI-compatible model chunks; Northstar executes tools and maps the result
  to Vercel UI-message chunks for web/mobile clients.
- Represent configured connections with user-defined gateway IDs and the one
  currently implemented type, `OPENAI_COMPATIBLE`. Adding a native Anthropic or
  Google adapter later may extend the core enum and registry; feature modules
  must not depend on provider-specific Spring AI options.
- Route by task. A route is a gateway ID plus model ID. Runtime database
  overrides win over application defaults; resetting Settings removes the
  override.
- The Chat model picker lives in Chat and is persisted per conversation. New
  conversations use the most recently selected Assistant model, falling back
  to the Assistant route default.
- Credentials and base URLs remain server-side. Settings may select among
  configured gateways and catalog models but never receives an API key.
- 9Router `/v1/search` and `/v1/web/fetch` are non-standard extensions and stay
  behind the existing web-research ports. They are not part of the generic
  OpenAI-compatible model gateway.
- Do not add resumable streams in this increment. Heartbeats and cancellation
  improve the live connection; resumability requires a durable active-stream
  store and a separate GET stream endpoint.

## Stream Contract

Normal turns emit message start, step boundaries, text/tool parts, message
finish, then `[DONE]`. Tool failures emit `tool-output-error`. Aborted turns
emit `abort` and `[DONE]`; other failures emit a safe `error` and `[DONE]`.
Heartbeat frames are SSE comments, never unknown `data` parts, so the strict AI
SDK chunk parser ignores them.

## Configuration Model

`northstar.ai` binds to immutable records. It contains configured gateway
definitions, a default gateway, task route defaults, catalog caching, and
stream timing. Persisted runtime settings store only gateway/model selections.
Embedding routes must retain the configured pgvector dimension.

## Verification

- Java unit tests pin exact Vercel frame ordering, heartbeat comments, error,
  abort, and tool-step behavior.
- API integration tests parse a real streamed response and verify persistence.
- Gateway tests cover configuration binding, route precedence, catalog
  allow-listing, capability validation, and per-request model overrides.
- Web and Flutter tests cover model selection, conversation persistence, and
  unsupported/loading/error states.
- Runtime verification uses the real SPA at desktop/mobile widths and `curl -N`
  through the reverse-proxy path when available.
