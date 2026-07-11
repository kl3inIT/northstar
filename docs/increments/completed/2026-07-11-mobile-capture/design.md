# Mobile Capture

## Outcome

Let an iPhone user capture text or a receipt photo, review the structured AI
draft, explicitly save it to the correct Northstar domain, and undo that save.

## Mobile Boundary

- Capture is opened as a focused modal-style route from Assistant; it is not a
  copy of the desktop Capture dashboard or another permanent tab.
- Text may be auto-classified or forced to Note, Task, Event, or Expense.
- Receipt intake uses the system camera/photo picker. The selected image is sent
  directly to `/api/capture/receipt` and is never stored by Northstar.
- Voice recording/transcription is a follow-on slice because its recording,
  interruption, and permission lifecycle is independent of image intake.

## AI Safety Boundary

- `/api/capture/draft` and `/api/capture/receipt` are ask-mode operations: they
  return untrusted structured proposals and do not persist data.
- Flutter validates the matching draft payload and shows an editable summary.
- Nothing is saved until the user taps the kind-specific confirmation button.
- Consequential finance fields always show type, amount, date, description,
  category, and exceptional status before confirmation.
- A successful write exposes Undo; an undo failure remains visible and can be
  retried. Provider keys, prompts, classification, and image reasoning stay on
  the backend.

## Layers

- `data/models`: validated Capture and saved-result DTOs.
- `data/services`: authenticated JSON/multipart requests and domain writes.
- `data/repositories`: draft validation plus deterministic mapping/write/undo.
- `domain/models`: Capture kind, draft variants, and saved result.
- `ui/features/capture/view_models`: input, loading, preview, save, error, retry,
  and undo state.
- `ui/features/capture/views`: compact Cupertino form and review surface.
