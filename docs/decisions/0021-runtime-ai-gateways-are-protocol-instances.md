# 0021 - Runtime AI Gateways Are Protocol Instances

## Status

Accepted on 2026-07-12. Supersedes the deployment-only credential portion of
[0020](./0020-ai-tasks-route-through-configured-gateways.md).

## Decision

Northstar implements protocol adapters in code and gateway instances in
configuration. The current adapter is `OPENAI_COMPATIBLE`; OpenAI, 9Router,
OpenRouter, LiteLLM, and Custom are UI presets that create the same runtime
gateway shape rather than provider branches.

Deployment YAML may define read-only gateway defaults. Authenticated Settings
may create, edit, connection-test, and delete runtime instances without a
restart. Runtime definitions contain a stable gateway id, display name, base
URL, manual model ids, `/models` discovery policy, timeout, and encrypted API
key. The API key uses an AES-256-GCM envelope with the gateway id as associated
data and a separately deployed base64 key; responses never echo the secret.

Native Anthropic or Gemini support may add a protocol adapter later. It must not
add provider conditionals to feature modules or treat a compatible vendor name
as a protocol.

## Consequences

- A user can connect multiple compatible endpoints and route workloads among
  them at runtime.
- Vendor presets improve setup without becoming architecture.
- `NORTHSTAR_AI_CREDENTIAL_KEY` must remain stable across deployments; losing
  it makes stored runtime credentials undecryptable.
- Deployment gateways remain the recovery path when runtime settings are
  unavailable.
