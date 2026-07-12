# Assistant TTS Design

## Problem

Northstar can transcribe live microphone input, but it cannot synthesize or
persist spoken output. Assistant responses, vocabulary cards, and future
shadowing exercises would otherwise each need separate provider calls and
storage behavior.

## Scope

- Add provider-neutral text-to-speech contracts and persisted, content-addressed
  speech assets.
- Route synthesis through a configured AI gateway with the
  `TEXT_TO_SPEECH` capability.
- Support the OpenAI `/audio/speech` contract directly and the normalized
  9Router `/audio/speech` endpoint.
- Generate audio only after an explicit user action.
- Reuse a stored audio asset when text and synthesis configuration match.
- Add an Assistant message action that synthesizes and plays the visible final
  response text.
- Keep the service reusable by vocabulary cards and shadowing without coupling
  it to Assistant persistence.

## Non-Goals

- No automatic playback or hands-free voice mode.
- No vocabulary-card or shadowing UI in this increment.
- No full-duplex realtime voice conversation.
- No speech-to-text changes.
- No automatic regeneration when a provider changes its implementation behind
  the same model identifier.

## Architecture

```text
Assistant message action
        |
        v
Speech API -> SpeechAssetService -> TextToSpeechGateway
                    |                       |
                    v                       v
              AttachmentService      OpenAI / 9Router
```

`SpeechAssetService` hashes normalized text plus gateway, target, locale, and
format. A cache hit returns the existing immutable attachment without
calling the provider. A cache miss synthesizes once, stores the bytes through
the attachment module, and persists the speech metadata.

The speech asset is not owned by an Assistant message. Future cards and
shadowing exercises can request the same synthesis key and reuse the same file.
Source-specific links can be added later without changing provider adapters or
duplicating audio bytes.

## Delivery

- Assistant messages keep text as the source of truth.
- The API returns speech metadata and an authenticated audio URL.
- Generated audio is served only through the speech endpoint, which can safely
  return the provider-produced audio MIME type without weakening general
  attachment upload protections.
- The chat model selector remains independent from the TTS route.
