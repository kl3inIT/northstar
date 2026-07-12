# Vocabulary Language and Deck Scoping - Design

## Goal

Separate English and Chinese vocabulary libraries and make review explicitly
deck-scoped without duplicating cards or changing Ebisu scheduling.

## Behavior

- Vocabulary has `English` and `Chinese` tabs. Stats, search, table rows, deck
  choices, and review CTA all follow the selected language.
- Deck selection offers `All decks`, `General`, and the distinct saved deck
  names for that language.
- Review calls require a language and optionally a deck. Omitting deck means
  all decks in that language; selecting General sends the explicit General
  scope for cards whose stored deck is null.
- Capture, Assistant, MCP, REST create, and edit contracts carry language and
  optional deck. Chinese can be detected from Han text; supported non-Han
  vocabulary defaults to English when older callers omit language.
- Changing language or deck edits only classification. Content, Ebisu model,
  suspension, and immutable review history remain intact.

## Data

`vocab_card.language` is required and constrained to `ENGLISH|CHINESE`.
`vocab_card.deck` is nullable, trimmed, case-insensitively canonicalized from
existing names, and capped at 80 characters. Migration V41 backfills existing
cards without an AI call.

## Gates

1. Core tests pin detection, language/deck filtering, canonicalization, and
   unchanged memory state on edits.
2. Controller/OpenAPI tests pin required review language and optional deck.
3. Web tests pin independent language/deck selection and request scope.
4. Run backend compile/core tests, web test/typecheck/build, and a browser
   walkthrough against the worktree API.
5. Consolidate Study spec/test matrix/roadmap and App Behavior, then move this
   increment to completed.
