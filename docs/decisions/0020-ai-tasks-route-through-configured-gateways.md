# 0020 - AI Tasks Route Through Configured Gateways

## Status

Accepted on 2026-07-11.

The deployment-only credential paragraph is superseded by
[0021](./0021-runtime-ai-gateways-are-protocol-instances.md). Task routing and
adapter boundaries remain accepted.

## Decision

Model-backed features depend on the `AiClientRouter` port and identify their
workload with `AiTask`. A route consists of a user-defined gateway id and model
id. Immutable `northstar.ai` properties provide defaults; database settings
override one task at runtime.

OpenAI direct, 9Router, OpenRouter, LiteLLM, and similar endpoints are instances
of the `OPENAI_COMPATIBLE` integration. The integration is shared by API and
worker so background image captioning honors the same route store. Native
Anthropic or Gemini support may add another adapter later without changing
feature services.

Chat stores its route per conversation and exposes a safe catalog to web and
mobile. Credentials, arbitrary headers, and base URLs remain deployment
configuration and are never returned to clients. Web-search/fetch endpoints are
separate capabilities behind the existing web-research ports, not model gateway
operations.

## Consequences

- Switching a route affects the next workload call without restart.
- Routing policy remains centralized instead of being copied into features.
- OpenAI-compatible parity is bounded by what the selected gateway implements.
- Native provider protocols require a new adapter and a deliberate core enum
  extension, but no feature-level rewrite.
