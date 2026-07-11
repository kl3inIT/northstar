# 0016 - Mobile Capture Reviews Before Write

## Status

Accepted on 2026-07-11.

## Context

Capture is valuable on a phone because text, photos, and short corrections are
available at the moment of intent. The AI response can still misclassify the
kind or extract consequential fields such as amount, date, and category
incorrectly. Persisting directly from a model response would hide those errors
and make mobile convenience more expensive than it appears.

The backend already separates structured drafting from domain persistence. It
also keeps provider prompts, models, and credentials away from clients.

## Decision

Treat mobile text and receipt extraction as ask-mode operations. Validate the
typed response, show an editable kind-specific Cupertino review, and write
nothing until the user explicitly confirms. After a successful write, retain
the created entity IDs so the complete action can be undone and retried if an
undo request fails.

Open Capture as a focused protected route from Assistant, not as another
permanent tab. Use the operating system image picker and never persist receipt
image bytes in Northstar. Deliver voice later as a separate increment because
recording permissions, interruptions, cancellation, and audio lifecycle form a
distinct native workflow.

## Consequences

- AI output remains advisory until a person reviews it.
- Expense batches are all visible before confirmation and undo as one user
  action even though the API deletes their entities individually.
- Capture stays fast to reach without crowding the five-tab mobile shell.
- Native voice capture and device accessibility remain explicit follow-on work.
