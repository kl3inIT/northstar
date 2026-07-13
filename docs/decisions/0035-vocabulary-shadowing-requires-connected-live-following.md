# 0035 — Vocabulary shadowing requires connected live following

Status: accepted. Supersedes the Shadowing fallback clause in decision 0032;
Word, Dictation, persistence, retention, provider-score, and FSRS decisions in
0032 remain accepted.

## Context

Northstar originally implemented Shadowing as `listen, then repeat` and fell
back from a missing example to the isolated card front. That made it almost the
same exercise as Word pronunciation. Language-teaching research instead defines
standard shadowing as repeating connected incoming speech simultaneously and as
accurately as possible. Script-assisted variants are valid, but isolated-word
repetition and post-playback imitation do not exercise the same online
phonological processing.

Azure scripted pronunciation assessment can score the learner recording against
reference text for Accuracy, Fluency, Completeness, and Prosody. It does not
compare the learner waveform with Northstar's TTS waveform, so those values must
not be presented as a model-synchronization score.

## Decision

- Word remains an isolated pronunciation exercise: listen, then record the card
  front.
- Shadowing requires a saved connected target-language example: at least four
  lexical words for spaced scripts or six Han characters. It never falls back
  to the card front.
- The persisted `example — translation` shape is split at the explicit separator;
  TTS, Shadowing, and example-based Dictation use only the target-language half.
- Starting Shadowing opens the microphone, then plays the model while recording
  continues. The learner follows one beat behind, and capture stops shortly
  after playback so the final phrase is retained. The visible script makes this
  a script-assisted shadowing variant; headphones are recommended to avoid
  playback leakage.
- Azure results remain provider-native pronunciation measurements. Northstar
  does not claim to measure timing similarity to the selected TTS voice.

## Consequences

The Word and Shadowing tabs now train different skills, cards without suitable
material explain how to unlock Shadowing, and generated bilingual examples no
longer send their translation to target-language TTS or pronunciation scoring.
True waveform/timestamp alignment remains a future capability if model-imitation
scoring becomes useful.

## Research basis

- Hamada and Suzuki, “Situating Shadowing in the Framework of Deliberate
  Practice: A Guide to Using 16 Techniques” (RELC Journal, 2024),
  <https://doi.org/10.1177/00336882221087508>.
- Hamada, “Developing a New Shadowing Procedure for Japanese EFL Learners”
  (RELC Journal, 2022), <https://doi.org/10.1177/0033688220937628>.
- Microsoft, “Use pronunciation assessment”,
  <https://learn.microsoft.com/azure/ai-services/speech-service/how-to-pronunciation-assessment>.
