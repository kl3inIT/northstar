# Runtime-First AI Gateways

## Problem

Northstar can store encrypted runtime gateways and route most AI tasks through
them, but the application still treats `OPENAI_API_KEY` as an implicit primary
configuration. The deployment-defined `openai` gateway is read-only, so changing
its key from Settings creates a second logical OpenAI connection. Embeddings,
server-side transcription, OpenAI web search, and realtime dictation also retain
direct environment-key paths.

This leaves the product UI more provider-neutral than the runtime actually is.

## Decision

Runtime Settings is the primary credential-management surface. Environment
credentials remain an optional deployment bootstrap and recovery fallback.

One gateway id represents one logical connection:

```text
encrypted Settings override
        > deployment environment credential
        > not configured
```

The existing `ai_gateway_setting` row may use the same id as a deployment
gateway. In that case it is an overlay, not a second gateway. Removing the
overlay restores the deployment definition without deleting task routes.

## Behavior

- The `openai` gateway appears once.
- Deployment-backed gateways are editable. Saving creates or updates an
  encrypted runtime overlay with the same id.
- Gateway responses expose whether the effective credential comes from
  Settings, the environment, or nowhere, plus whether a deployment fallback and
  runtime override exist.
- Resetting a deployment-backed override restores the environment credential.
  Deleting a pure runtime gateway retains the existing destructive semantics.
- A missing provider key never prevents the API or worker context from loading.
  AI calls against an unconfigured route fail at the capability boundary while
  non-AI features remain available.
- Chat, embedding, ordinary transcription, OpenAI web search, and realtime
  transcription all resolve the selected gateway at call time.
- Realtime transcription is a separate AI task because an ordinary STT endpoint
  does not imply ephemeral Realtime credential support.
- Pgvector remains fixed at 1536 dimensions. Runtime embedding models are asked
  for that width; changing vector width remains a schema migration, not a UI
  setting.

## Security

- Provider keys are never returned to clients.
- Runtime keys stay AES-256-GCM encrypted with gateway id as associated data.
- `NORTHSTAR_AI_CREDENTIAL_KEY` remains a required server-owned master key for
  saving runtime credentials. It is not a provider dependency and cannot be
  moved into the same database it protects.
- Environment provider keys remain supported for immutable/headless deploys but
  are optional.

## Non-Goals

- Native Anthropic or Gemini protocols.
- Pretending a chat-compatible endpoint supports Realtime.
- Runtime changes to pgvector dimensions.
- Automatic migration of an environment secret into the database.

## Verification

- API context loads with no `OPENAI_API_KEY`.
- Editing `openai` produces one configured descriptor backed by Settings.
- Resetting it restores the environment/none state without deleting routes.
- Routed embedding and transcription tests assert the selected base URL, key,
  model, and embedding dimensions.
- Realtime session tests assert gateway routing and the returned WebSocket URL.
- Web search has no credential fallback outside the shared gateway registry.
- Desktop and mobile-width Settings checks verify the single-row credential
  source, edit, reset, and unconfigured states.
