# 0027 - Runtime Gateway Credentials Override Environment Fallbacks

## Status

Accepted on 2026-07-12. This refines the deployment/runtime precedence in
[0021](./0021-runtime-ai-gateways-are-protocol-instances.md).

## Decision

One gateway id represents one logical connection. When a Settings gateway row
uses the same id as a deployment gateway, it is an encrypted runtime overlay:

```text
Settings credential > environment credential > not configured
```

The merged descriptor exposes the effective credential source without exposing
the credential. Editing a deployment-backed row creates or updates its overlay.
Reset removes only that overlay and preserves task, conversation, and web routes.
Delete remains destructive only for independent runtime gateways.

Provider credentials are resolved at AI call time. API and worker must start
without `OPENAI_API_KEY`; chat, web search, embedding, STT, image, speech, and
Realtime operations fail at their gateway boundary when no effective credential
exists. `NORTHSTAR_AI_CREDENTIAL_KEY` remains a stable server-owned master key
for encrypting runtime credentials and is not a provider dependency.

Realtime transcription is its own task and capability. The API returns the
selected gateway's WebSocket URL alongside an ephemeral secret, so the browser
does not encode a provider endpoint. Embeddings remain fixed at 1536 dimensions
to match the pgvector schema.

## Consequences

- Settings is the primary credential-management surface.
- Immutable/headless deployments may keep environment-only gateway credentials.
- A provider key change does not require an application restart.
- There is no duplicate OpenAI row just to replace an environment key.
- Losing the master encryption key still makes runtime credentials unreadable;
  an environment fallback remains a recovery path.
