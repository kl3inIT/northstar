# 0024 - Vocabulary Review Is a Focused Page Workflow

## Status

Accepted.

## Context

Decision 0015 intentionally paired Ebisu recall probability with chat review:
the user was expected to ask the Assistant to quiz cards and `/study` only
reported memory state. In practice, reviewing many cards needs a stable visual
surface, reveal-before-rating, keyboard controls, pronunciation actions, and a
clear place for optional card enrichment. A chat transcript makes those
controls transient and forces the learner to revisit earlier messages.

The scheduling rationale of decision 0015 still holds. Missed days must not
manufacture a due-date backlog, and the existing append-only review history
keeps future algorithm changes possible.

## Decision

- `/study` Vocabulary becomes the canonical focused review surface. Chat
  remains an optional natural-language entry point, not the only reviewer.
- The review queue remains the lowest predicted-recall active cards; there are
  no due dates or displayed interval promises.
- The learner alone records Again/Hard/Good/Easy. AI answer feedback is
  advisory and cannot update the memory model.
- Reading (IPA/pinyin) and part of speech are the minimum AI-native metadata
  fields for a newly captured card when known.
- Additional AI content is generated only after an explicit user action,
  previewed without persistence, and applied through the ordinary card update.

This decision supersedes decision 0015 only where it says reviews must happen
in chat or that Northstar must not provide a focused flashcard workflow. It
does not supersede the Ebisu model, at-risk ranking, no-backlog behavior,
rating-to-success mapping, or append-only review log.

## Consequences

The Study page becomes an action surface as well as a report, while the core
memory behavior stays stable. The API gains thin review and coaching endpoints.
AI cost is visible and controllable because loading, revealing, and rating a
card never trigger enrichment. Future FSRS adoption remains a separate
decision rather than an accidental UI side effect.

