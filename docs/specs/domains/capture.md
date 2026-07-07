# Capture Spec

## Current Behavior

- Capture is the human-facing AI write path for raw thoughts and audio.
- The capture flow can turn raw text into a structured note draft with title,
  folder, tags, cleaned Markdown, and wiki-link friendly content.
- Voice/audio capture delegates transcription through the API delivery app.
- The `core.capture` module stays provider-agnostic; the delivering app wires
  the `ChatClient`.

## Intended Direction

Future capture should also parse raw input into structured entities such as
tasks, expenses, study logs, habit logs, decisions, and project memories. Until
that code lands, this remains product intent rather than current behavior.

## Source Modules

- `core.capture`
- `apps/api.capture`
- `web/src/pages/capture.tsx`
