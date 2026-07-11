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
| Mobile draft is non-persistent until confirmation | Automated | Capture repository and ViewModel tests verify draft, edit, and explicit save boundaries. |
| Mobile receipt upload preserves image MIME and bytes | Automated | Capture service tests verify authenticated multipart transport. |
| Mobile batch save and undo | Automated | Repository and ViewModel tests verify all saved IDs are deleted and failed undo can be retried. |
| Compact and dark Cupertino review surfaces | Automated | Widget tests cover 390x844 text review and dark-mode receipt finance fields. |
| Real mobile Web preview against local API | Runtime | Chromium walkthrough verified forced Note draft `200`, note write `201`, and undo delete `204` at 390x844. |
