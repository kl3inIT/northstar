# Vocabulary Review — Plan

## Block A — Contract and default card shape

- Add decision 0024 and this increment documentation.
- Extend Capture/Assistant card input and metadata helpers with
  `partOfSpeech`; reading and part of speech are default, generated examples
  are not.
- Add metadata parsing/merging tests.
- End green on backend compile/core tests and web typecheck.
- Commit: `feat(study): define vocabulary review card contract`.

## Block B — Review and opt-in AI APIs

- Add the review queue and manual rating REST endpoints.
- Add provider-neutral answer assessment and enrichment preview records plus a
  `VocabCoach` wired only by `apps/api` through the existing `STUDY_GRADER`
  route.
- Validate structured output and preserve existing metadata on merge.
- Add core/API tests; regenerate OpenAPI and the web client.
- End green on the local gates.
- Commit: `feat(study): add vocabulary review coaching api`.

## Block C — shadcn reviewer

- Replace the chat-only hint with `Review weak cards` and a 10/20/30 selector.
- Implement the front, checked, revealed, completion, keyboard, and responsive
  states using existing shadcn components.
- Reuse realtime dictation for optional spoken input and the existing
  pronunciation dialog after reveal.
- Implement the opt-in enrichment Sheet with generate, preview, apply, and
  discard states. Opening/revealing must not call the model.
- Add focused web tests; end green on the local gates.
- Commit: `feat(web): add vocabulary card review workflow`.

## Block D — Runtime verification and consolidation

- Run the full terminating test/build gates.
- Walk the real review flow in a browser, including a network check proving
  enrichment is request-driven.
- Update the Study spec, test matrix, roadmap, and Northstar App Behavior.
- Move this increment to `completed/`.
- Commit: `docs(study): consolidate vocabulary review workflow`.
