# Capture Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Capture service integration | Automated | `apps/api/src/test/java/com/northstar/api/capture/CaptureServiceIntegrationTests.java` |
| Voice transcription delegation | Automated | Covered by the capture integration test. |
| Live capture UI flow | Gap | Needs Playwright coverage when the capture workflow changes. |
| Multi-entity extraction | Not applicable yet | Future behavior from `docs/vision.md`, not current shipped behavior. |
