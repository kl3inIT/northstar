# Assistant TTS Plan

## Foundation

- [x] Add a `TEXT_TO_SPEECH` workload route and safe defaults.
- [x] Add Flyway schema and provider-neutral speech asset domain types.
- [x] Add deterministic synthesis-key validation and cache behavior.
- [x] Implement OpenAI and 9Router `/audio/speech` delivery.

## API

- [x] Add on-demand synthesis and authenticated audio-serving endpoints.
- [x] Add TTS target discovery for compatible gateways.
- [x] Cover cache hits, validation, capability checks, and provider failures.
- [x] Regenerate the OpenAPI contract and web client.

## Web

- [x] Add the official AI Elements audio player.
- [x] Add a message speech action with loading, playback, retry, and error states.
- [x] Ensure only final visible assistant text is synthesized.
- [x] Verify responsive behavior and reduced-motion-safe states.

## Verification And Consolidation

- [x] Run backend compile, core tests, web typecheck/build, and clean test.
- [x] Walk the Assistant synthesis/cache flow in a real browser.
- [x] Audit the changed Assistant surface at a mobile viewport.
- [x] Update architecture, current behavior specs, test matrices, and roadmap.
- [x] Move this increment to `completed` after all gates pass.
