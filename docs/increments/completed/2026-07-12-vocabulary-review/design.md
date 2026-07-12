# Vocabulary Review — Design

## Goal

Move vocabulary review from a chat-only protocol into a focused, keyboard-first
surface inside `/study` while preserving Northstar's recall-probability model.
Cards created from natural language have a useful minimum shape by default;
costlier AI enrichment is explicit, previewed, and saved only after approval.

## Decisions

### D1 — The Vocabulary page owns focused review

The Vocabulary tab remains the library and gains `Review weak cards`. A review
session draws up to 10/20/30 active cards with the lowest predicted recall.
Chat may still start or conduct a quiz, but it is no longer the only review
surface. This supersedes only the chat-only UI consequence of decision 0015;
Ebisu and the absence of due dates remain unchanged.

### D2 — A base card has pronunciation and part of speech

`front` remains the lexical item and `back` its meaning. Metadata JSON gains a
stable `partOfSpeech` key alongside `reading` (tone-marked pinyin for Chinese,
IPA for English). New Assistant and Capture cards populate both when known and
use an empty value rather than guessing. Existing cards remain valid.

An example is no longer generated merely because a card is created. A
user-supplied example may be preserved, but generated examples belong to the
explicit enrichment flow.

### D3 — AI never enriches on page load or reveal

`Enrich card` opens a Sheet without calling a model. The user selects any of:

- example with translation;
- collocations;
- synonyms and antonyms;
- contrast with easily confused words;
- mnemonic.

Only `Generate selected` calls the `STUDY_GRADER` route. The server returns a
validated preview plus merged metadata but does not persist it. `Apply to card`
uses the ordinary card update endpoint; `Discard` writes nothing. Existing
user-authored values are not silently overwritten.

Image generation is not included: Northstar has no image-generation provider
contract. The UI must not ship a dead or misleading action.

### D4 — Answer checking and scheduling are separate

The learner may type or dictate a meaning, then explicitly press `Check
answer`. The LLM returns `CORRECT`, `CLOSE`, or `MISSED` with one short grounded
explanation. `Show answer` performs no AI call. The assessment never selects a
rating or updates memory.

After reveal, the learner presses Again/Hard/Good/Easy. The API maps those to
the existing Ebisu success values and appends a `MANUAL` review log. No fake
future intervals or due dates are displayed.

### D5 — Reviewer interaction follows proven shadcn patterns

The implementation is original and uses the repo's shadcn New York/zinc tokens:

- progress and exit in a compact session header;
- one dominant `Card`, with front and answer states instead of a chat thread;
- reveal before rating;
- keyboard shortcuts: Space reveal, 1-4 rate, R listen, Escape exit;
- `Sheet` for opt-in enrichment and `Dialog` for editing/pronunciation;
- mobile actions remain at least 44px and the enrichment Sheet occupies the
  viewport rather than compressing the card.

The interaction borrows patterns, not code, from lmscn's flashcard registry,
Deep Student's keyboard session, and Flash Fathom's review state.

### D6 — Sessions are deliberately lightweight

The browser owns the ephemeral queue and tally. The server remains authoritative
for card content, current recall, and each rating. Closing the page loses only
the remaining local queue, never recorded reviews. After each rating the next
card appears; the completion screen shows the four-rating tally and offers
`Review more` or `Back to vocabulary`.

## API

- `GET /api/study/vocab/review?limit=20` — lowest-recall active cards.
- `POST /api/study/vocab/{id}/reviews` with `rating` — update Ebisu and log a
  manual review.
- `POST /api/study/vocab/{id}/answer-check` with `answer` — explicit semantic
  assessment, no persistence.
- `POST /api/study/vocab/{id}/enrichment` with selected fields — explicit
  preview, no persistence.
- Existing `PUT /api/study/vocab/{id}` applies an accepted metadata preview.

## Metadata shape

Unknown keys are preserved and clients ignore fields they do not understand.

```json
{
  "reading": "/məˈtɪkjələs/",
  "partOfSpeech": "adjective",
  "example": "She keeps meticulous records. — Cô ấy lưu hồ sơ rất tỉ mỉ.",
  "collocations": ["meticulous planning", "meticulous attention"],
  "synonyms": ["careful", "thorough"],
  "antonyms": ["careless"],
  "contrast": "Meticulous emphasizes close attention to every detail.",
  "mnemonic": "Think: minute details."
}
```

## Verification

1. Unit tests pin metadata merge, rating mapping, answer validation, and
   enrichment validation.
2. API tests cover queue limit, manual review, answer check, and non-persisting
   enrichment preview.
3. Web tests cover front/reveal/rating, keyboard behavior, opt-in enrichment,
   preview apply/discard, and completion.
4. Local gates: `./gradlew --no-daemon compileJava compileTestJava :core:test`
   and `pnpm -C web typecheck` after each implementation block.
5. Final context gate: `./gradlew --no-daemon clean test` plus web build.
6. Browser E2E proves no enrichment request occurs before `Generate selected`,
   a rating advances the queue, and default reading/part-of-speech render.
7. Consolidate study spec/test matrix, this decision, roadmap, completed
   increment location, and Northstar App Behavior.
