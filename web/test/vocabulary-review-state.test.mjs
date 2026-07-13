import assert from 'node:assert/strict'
import test from 'node:test'
import {
  EMPTY_TALLY,
  cardMatchesDeck,
  comparableAudioTrendAttempts,
  deckQuery,
  directionIsDue,
  audioPracticeReference,
  exampleAudioAssetId,
  examplePracticeText,
  enrichmentFieldsForRequest,
  incrementRating,
  parseDictationDiff,
  reviewIsComplete,
  reviewKeyboardAction,
  wordAudioAssetId,
  shadowingPracticeReference,
} from '../src/features/study/vocabulary-review-state.ts'

test('review stays on the front until reveal and completes after the last rating', () => {
  assert.equal(reviewIsComplete(0, 1), false)
  const tally = incrementRating(EMPTY_TALLY, 'GOOD')
  assert.deepEqual(tally, { AGAIN: 0, HARD: 0, GOOD: 1, EASY: 0 })
  assert.equal(reviewIsComplete(1, 1), true)
})

test('keyboard shortcuts flip both card sides and respect typing and enrichment states', () => {
  assert.deepEqual(reviewKeyboardAction(' ', { enrichmentOpen: false, typing: false, revealed: false }), { type: 'flip' })
  assert.deepEqual(reviewKeyboardAction(' ', { enrichmentOpen: false, typing: false, revealed: true }), { type: 'flip' })
  assert.deepEqual(reviewKeyboardAction('3', { enrichmentOpen: false, typing: false, revealed: true }), { type: 'rate', rating: 'GOOD' })
  assert.deepEqual(reviewKeyboardAction('r', { enrichmentOpen: false, typing: false, revealed: false }), { type: 'listen' })
  assert.deepEqual(reviewKeyboardAction('Escape', { enrichmentOpen: false, typing: true, revealed: false }), { type: 'exit' })
  assert.equal(reviewKeyboardAction(' ', { enrichmentOpen: false, typing: true, revealed: false }), null)
  assert.equal(reviewKeyboardAction('3', { enrichmentOpen: true, typing: false, revealed: true }), null)
})

test('selecting enrichment is inert until Generate is explicit', () => {
  const selected = new Set(['EXAMPLE', 'COLLOCATIONS'])
  assert.equal(enrichmentFieldsForRequest(selected, false), null)
  assert.deepEqual(enrichmentFieldsForRequest(selected, true), ['EXAMPLE', 'COLLOCATIONS'])
  assert.equal(enrichmentFieldsForRequest(new Set(), true), null)
})

test('deck scopes never leak cards and All decks omits the deck query', () => {
  assert.equal(cardMatchesDeck('IELTS', 'IELTS'), true)
  assert.equal(cardMatchesDeck('Daily', 'IELTS'), false)
  assert.equal(cardMatchesDeck(undefined, 'General'), true)
  assert.equal(cardMatchesDeck('IELTS', 'General'), false)
  assert.equal(cardMatchesDeck('HSK4', 'ALL'), true)
  assert.equal(deckQuery('ALL'), undefined)
  assert.equal(deckQuery('HSK4'), 'HSK4')
})

test('due state excludes future and sibling-buried directions', () => {
  const now = Date.parse('2026-07-12T10:00:00Z')
  assert.equal(directionIsDue('2026-07-12T09:00:00Z', undefined, now), true)
  assert.equal(directionIsDue('2026-07-12T11:00:00Z', undefined, now), false)
  assert.equal(directionIsDue('2026-07-12T09:00:00Z', '2026-07-13T00:00:00Z', now), false)
})

test('applied audio is reused only while its bound text is unchanged', () => {
  const metadata = {
    example: 'We met by chance.',
    frontAudioAssetId: 'word-audio',
    frontAudioText: 'serendipity',
    exampleAudioAssetId: 'example-audio',
    exampleAudioText: 'We met by chance.',
  }
  assert.equal(wordAudioAssetId('serendipity', metadata), 'word-audio')
  assert.equal(wordAudioAssetId('fortuity', metadata), undefined)
  assert.equal(exampleAudioAssetId(metadata), 'example-audio')
  assert.equal(exampleAudioAssetId({ ...metadata, example: 'Changed.' }), undefined)
})

test('shadowing and dictation use the saved example then fall back to the card front', () => {
  const metadata = { example: 'We met by pure chance. — Chúng tôi tình cờ gặp nhau.' }
  assert.equal(examplePracticeText(metadata), 'We met by pure chance.')
  assert.equal(audioPracticeReference('serendipity', metadata, 'WORD'), 'serendipity')
  assert.equal(audioPracticeReference('serendipity', metadata, 'SHADOWING'), 'We met by pure chance.')
  assert.equal(audioPracticeReference('serendipity', metadata, 'DICTATION'), 'We met by pure chance.')
  assert.equal(audioPracticeReference('serendipity', {}, 'SHADOWING'), '')
  assert.equal(audioPracticeReference('serendipity', {}, 'DICTATION'), 'serendipity')
})

test('shadowing requires connected speech in spaced or Han-script examples', () => {
  assert.equal(shadowingPracticeReference({ example: 'Take it.' }), undefined)
  assert.equal(shadowingPracticeReference({ example: 'Please take a seat.' }), 'Please take a seat.')
  assert.equal(shadowingPracticeReference({ example: '我们明天一起去学校。 — Ngày mai chúng ta cùng đi học.' }), '我们明天一起去学校。')
})

test('example audio binding follows the target sentence rather than its translation', () => {
  const metadata = {
    example: 'We met by chance. — Chúng tôi tình cờ gặp nhau.',
    exampleAudioAssetId: 'example-audio',
    exampleAudioText: 'We met by chance.',
  }
  assert.equal(exampleAudioAssetId(metadata), 'example-audio')
})

test('audio trends never mix practice modes or provider scales', () => {
  const attempts = [
    { id: '1', mode: 'WORD', providerId: 'azure', accuracy: 90 },
    { id: '2', mode: 'SHADOWING', providerId: 'azure', accuracy: 80 },
    { id: '3', mode: 'WORD', providerId: 'other', accuracy: 70 },
    { id: '4', mode: 'WORD', providerId: 'azure', accuracy: 60 },
  ]
  assert.deepEqual(comparableAudioTrendAttempts(attempts, 'WORD').map((attempt) => attempt.id), ['1', '4'])
  assert.deepEqual(comparableAudioTrendAttempts(attempts, 'SHADOWING').map((attempt) => attempt.id), ['2'])
})

test('dictation diff parsing is lenient and never exposes malformed JSON to the UI', () => {
  assert.deepEqual(parseDictationDiff('[{"kind":"MATCH","expected":"we","actual":"we"},{"kind":"MISSING","expected":"met","actual":null},{"kind":"SUBSTITUTION","expected":"pure","actual":"extra"}]'), [
    { kind: 'MATCH', expected: 'we', actual: 'we' },
    { kind: 'MISSING', expected: 'met', actual: undefined },
    { kind: 'SUBSTITUTION', expected: 'pure', actual: 'extra' },
  ])
  assert.deepEqual(parseDictationDiff('not-json'), [])
})
