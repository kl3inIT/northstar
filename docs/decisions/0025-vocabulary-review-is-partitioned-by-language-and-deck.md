# 0025 - Vocabulary Review Is Partitioned by Language and Deck

## Status

Accepted.

## Context

The first focused reviewer ranked every active vocabulary card together. That
mixes English and Chinese prompts in one session even though the learner
chooses a language before choosing what material to practise. An Anki-style
deck tree would separate material, but copying cards between overlapping decks
would also duplicate Northstar's per-card memory state.

## Decision

- Every card belongs to exactly one language: `ENGLISH` or `CHINESE`.
- Vocabulary presents English and Chinese as separate libraries and never
  mixes their default review queues.
- A card may have one optional flat deck name inside its language. No deck
  means `General`; `All decks` is a query scope, not a stored deck.
- Review selection is `language` plus an optional deck. Ebisu still ranks the
  lowest-recall cards inside that scope and changing a card's deck never resets
  its model or review history.
- A card remains one memory object. Future cross-cutting classifications use
  tags or saved filters instead of copying the card into multiple decks.
- Existing cards are deterministically backfilled: a front containing a Han
  ideograph is Chinese; every other existing card is English. Language and deck
  remain editable.

## Consequences

English/IELTS, English/Daily, Chinese/HSK4, and similar sessions are predictable
without importing Anki's note templates, nested deck scheduler settings, or
filtered-deck machinery. The database gains queryable language and deck fields;
optional deck labels stay lightweight until real usage justifies deck CRUD or
hierarchy.
