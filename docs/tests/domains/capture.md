# Capture Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Capture service integration | Automated | `apps/api/src/test/java/com/northstar/api/capture/CaptureServiceIntegrationTests.java` |
| Voice transcription delegation | Automated | Covered by the capture integration test. |
| Note/task/event structured drafting | Automated | Capture integration tests exercise the real ChatClient structured-output mapping with a mocked model. |
| Multi-item expense drafting | Automated | Capture integration tests assert every amount is retained and the constrained vocabulary is present in the prompt. |
| Banking SMS and category-learning prompt | Automated | Capture integration tests verify `GD` is preferred over `SD`, message dates and multi-SMS handling are instructed, and recent corrected descriptions are included. |
| Multimodal receipt drafting | Automated | Capture integration tests exercise `draftFromImage` and its forced expense prompt. |
| Live Capture UI flow | Runtime | Gate 3 verifies the expense selector and receipt picker; live provider extraction remains opt-in. |
