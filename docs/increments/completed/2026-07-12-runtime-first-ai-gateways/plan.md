# Plan

## Block 1 - Gateway Overlay

- [x] Let runtime settings shadow deployment gateways by id.
- [x] Add credential-source, deployment-backed, and override metadata.
- [x] Preserve routes when resetting a deployment overlay.
- [x] Add backend tests for one-row OpenAI edit/reset behavior.

Gate: integration module tests and API gateway integration test green.

## Block 2 - Runtime-Routed Models

- [x] Remove OpenAI model auto-configuration from API and worker.
- [x] Provide routed embedding and ordinary transcription models.
- [x] Keep pgvector at 1536 dimensions and degrade search safely when no embedding
  gateway is configured.
- [x] Remove the direct-key OpenAI web-search path.

Gate: compile, routed model tests, core Modulith tests, and no-key context load.

## Block 3 - Realtime

- [x] Add a Realtime transcription task and OpenAI capability target.
- [x] Mint ephemeral credentials through the selected gateway.
- [x] Return the provider WebSocket URL and consume it in the web client.

Gate: API controller test and web typecheck green.

## Block 4 - Settings UX And Contract

- [x] Regenerate OpenAPI and Hey API output.
- [x] Show credential source and a single logical gateway.
- [x] Allow deployment-backed editing and reset-to-environment; reserve delete for
  pure runtime gateways.
- [x] Add the Realtime route selector.

Gate: web build and desktop/mobile browser walkthrough green.

## Block 5 - Consolidation And Release

- [x] Remove stale direct-key properties and test fixtures.
- [x] Update specs, test matrices, architecture, environment templates, and README.
- [x] Run full backend, web, responsive runtime, diff, and secret checks.
- [x] Prepare the scoped commit after all local gates pass; publish and inspect CI.
