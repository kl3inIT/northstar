# Capture Spec

## Current Behavior

- Capture is the single human-facing AI entry point for text, voice, and receipt
  images. Domain pages, including Finance, do not duplicate these controls.
- Raw text is classified by intent as a note, task, calendar event, or expense.
  The model reasons before choosing the kind; a forced kind skips
  classification and only shapes the matching draft.
- Notes receive a title, existing-folder-aware path, tags, cleaned Markdown, and
  wiki-link friendly content. Tasks receive optional deadlines and project
  discipline context. Events receive an absolute date/time span.
- Money already spent or received becomes an expense draft; an intention to buy
  remains a task. A single message can yield several expense/income items.
- Pasted banking SMS uses the same text input. Extraction distinguishes the
  transaction amount from the balance, honors the message timestamp, and can
  produce several drafts from several messages.
- Recent transaction-category corrections are included as user-specific prompt
  examples so repeated descriptions follow the user's vocabulary without a
  separate fine-tuning pipeline.
- Voice delegates transcription through the API delivery app, then follows the
  same text path.
- Receipt images follow a forced multimodal expense path and are not stored.
- The web persists the shaped entity and shows an undo action. Expense items are
  written in one batch and the echo names every amount and category.
- Mobile opens Capture as a focused route from Assistant instead of adding a
  permanent tab. Text and a system-picked receipt image produce an untrusted,
  editable draft; no entity is written before kind-specific confirmation.
- Mobile shows the consequential expense fields before saving, writes all
  receipt items as one reviewed action, and can undo every entity created by
  that action. A failed undo remains visible and retryable.
- Mobile voice capture is deferred until its recording, permission,
  interruption, and transcription lifecycle can be delivered as a separate
  increment.
- The `core.capture` module stays provider-agnostic; the delivering app wires
  the `ChatClient` and transcription model.

## Intended Direction

Future capture can add study logs, habit logs, decisions, and project memories
without adding per-domain entry forms.

## Source Modules

- `core.capture`
- `apps/api.capture`
- `web/src/pages/capture.tsx`
- `mobile/lib/ui/features/capture`
- `mobile/lib/data/repositories/capture_repository.dart`

## Related Specs

- [Finance](finance.md)
