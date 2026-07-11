# 0015 - Mobile Assistant Uses A Headless Chat Component

## Status

Accepted on 2026-07-11.

## Context

Northstar needs a maintained Flutter chat engine without allowing a Material
component kit or a client-side model SDK to dictate its iPhone experience or
backend protocol. The existing API already owns authentication, model
orchestration, tools, conversation persistence, and AI SDK-compatible SSE.

Flutter AI Toolkit provides an integrated provider-oriented AI experience, but
adopting its provider stack would duplicate Northstar backend responsibilities.
Building message virtualization, grouping, updates, and scrolling from scratch
would also create maintenance work unrelated to the product.

## Decision

Use `flutter_chat_ui` with `flutter_chat_core` as the backend-agnostic message
list/controller foundation. Replace its Material-oriented presentation with
Northstar-owned Cupertino composer, empty, message, Markdown, waiting, and tool
workflow builders.

Keep SSE parsing, access-token refresh, DTO validation, domain events,
conversation state, retry, and stop behavior in Northstar's service,
repository, and `Listenable` ViewModel layers. Keep secrets, prompts, models,
and action tools on the backend.

## Consequences

- The app benefits from a maintained chat engine while retaining an iOS-native
  product language.
- Transport and agent behavior remain independently testable and replaceable.
- Package upgrades require checking builder/controller API compatibility.
- Native iPhone rendering, VoiceOver, and Sideloadly installation still require
  device validation.

## References

- <https://pub.dev/packages/flutter_chat_ui>
- <https://flyer.chat/docs/flutter/introduction/>
- <https://docs.flutter.dev/ai/ai-toolkit>
- <https://developer.apple.com/design/human-interface-guidelines/generative-ai>
