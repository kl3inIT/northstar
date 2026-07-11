# iOS Sideload IPA And Mobile Assistant

## Outcome

Produce a Sideloadly-ready IPA without Apple signing secrets and replace the
mobile Assistant placeholder with a production-backed Cupertino conversation.

## IPA Boundary

- Continue compiling `Runner.app` with `--no-codesign` on GitHub-hosted macOS.
- Inject the public API base URL with `NORTHSTAR_API_BASE_URL`; prefer the
  repository's existing `NORTHSTAR_BASE_URL` variable and allow a manual input.
- Preserve the stable bundle ID and set the build number from the Actions run.
- Package exactly `Payload/Runner.app`, validate with `unzip -t`, generate a
  SHA-256 file, and retain the artifact for seven days.
- Do not put an Apple ID, password, certificate, provisioning profile, JWT
  secret, or provider API key into the IPA workflow.

## Flutter Layers

- `data/models`: validated assistant DTOs and stream frames.
- `data/services`: authenticated HTTP/SSE transport with one refresh-and-retry
  attempt on `401`.
- `data/repositories`: mapping from transport frames/history to domain models.
- `domain/models`: immutable conversation, message, role, and tool activity.
- `ui/features/assistant/view_models`: conversation selection, streaming,
  cancellation, failure, retry, and immutable presentation state.
- `ui/features/assistant/views`: compact/expanded Cupertino UI only.

## Failure Boundaries

- Unknown SSE frame types are ignored for forward compatibility; malformed
  JSON, protocol error frames, HTTP errors, and premature stream completion
  become typed failures.
- An access token is held only in memory. A single-flight refresh rotation
  prevents concurrent `401` responses from replaying the same refresh token.
- The transcript shows a waiting state before the first visible delta or tool
  event. Partial output remains visible if the stream later fails.
- The user can stop a stream or explicitly retry a failed response. The UI
  never silently claims a failed agent action completed.
