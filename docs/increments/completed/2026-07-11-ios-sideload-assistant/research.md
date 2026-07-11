# iOS Sideload And Assistant Research

## Sideloadly

- Sideloadly accepts an IPA, signs it with the user's Apple ID, and installs it
  from Windows or macOS. Its official FAQ says free Apple Developer accounts
  normally expire after seven days; overwriting an installed app requires the
  same Apple ID and bundle ID.
  - <https://sideloadly.io/index.html>
  - <https://sideloadly.io/faq>
- Northstar therefore does not need App Store signing secrets for this path.
  CI must compile an unsigned device app, package it as
  `Payload/Runner.app` inside an IPA archive, validate the archive, and publish
  a checksum. Sideloadly performs the device-specific signing step later.
- `io.github.kl3init.northstar` remains stable so an updated IPA can overwrite
  the previous sideload without losing app-local secure storage.

## Assistant On iOS

- Apple's generative-AI guidance says to identify AI usage, keep people in
  control, provide dismiss/retry/revert paths, protect privacy, design for
  nondeterministic failures, and preserve a useful fallback experience.
  - <https://developer.apple.com/design/human-interface-guidelines/generative-ai>
- Flutter's AI guidance treats model output as untrusted input, recommends
  ask/read-only behavior before agent behavior, keeps deterministic state in
  Dart, and requires explicit loading, partial, failure, retry, confirmation,
  and tool-loop states.
  - <https://docs.flutter.dev/ai/best-practices>
- Flutter AI Toolkit provides provider-oriented chat widgets, but Northstar
  already owns authentication, memory, prompts, model selection, tools, and an
  AI SDK UI Message Stream v1 endpoint in the backend. A thin typed adapter to
  that existing SSE protocol is smaller and does not expose provider keys.

## Existing Contract

- `POST /api/assistant/chat` accepts `message`, `conversationId`, and optional
  attachment IDs, then emits SSE `data:` frames.
- Frames include `start`, `text-start`, `text-delta`, `text-end`,
  `tool-input-start`, `tool-input-available`, `tool-output-available`, `error`,
  `finish`, and `[DONE]`.
- `/history` rehydrates text and persisted tool parts; `/conversations` lists
  durable conversations; delete remains a consequential operation and is not
  part of this first mobile slice.

## Product Direction

- Compact iPhone: one focused transcript, a keyboard-safe multiline composer,
  a history sheet, New Chat, visible waiting state, and restrained tool rows.
- Expanded windows: history and transcript can sit side by side without
  stretching readable message content.
- The client never renders raw tool JSON as trusted UI. It shows tool name and
  deterministic progress only; backend text remains the user-facing result.
