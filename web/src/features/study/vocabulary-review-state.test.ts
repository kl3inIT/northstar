import { describe, expect, test } from 'vitest'
import type { VocabAudioAttempt } from '@/lib/study-api'
import {
  EMPTY_TALLY,
  audioPracticeReference,
  cardMatchesDeck,
  comparableAudioTrendAttempts,
  deckQuery,
  directionIsDue,
  enrichmentFieldsForRequest,
  exampleAudioAssetId,
  examplePracticeText,
  incrementRating,
  parseDictationDiff,
  reviewIsComplete,
  reviewKeyboardAction,
  shadowingPracticeReference,
  wordAudioAssetId,
} from './vocabulary-review-state'

describe('vocabulary review state', () => {
  test('stays on the front until reveal and completes after the last rating', () => {
    expect(reviewIsComplete(0, 1)).toBe(false)
    expect(incrementRating(EMPTY_TALLY, 'GOOD')).toEqual({ AGAIN: 0, HARD: 0, GOOD: 1, EASY: 0 })
    expect(reviewIsComplete(1, 1)).toBe(true)
  })

  test('keyboard shortcuts flip both sides and ignore typing or enrichment states', () => {
    expect(reviewKeyboardAction(' ', { enrichmentOpen: false, typing: false, revealed: false })).toEqual({ type: 'flip' })
    expect(reviewKeyboardAction(' ', { enrichmentOpen: false, typing: false, revealed: true })).toEqual({ type: 'flip' })
    expect(reviewKeyboardAction('3', { enrichmentOpen: false, typing: false, revealed: true })).toEqual({ type: 'rate', rating: 'GOOD' })
    expect(reviewKeyboardAction('r', { enrichmentOpen: false, typing: false, revealed: false })).toEqual({ type: 'listen' })
    expect(reviewKeyboardAction('Escape', { enrichmentOpen: false, typing: true, revealed: false })).toEqual({ type: 'exit' })
    expect(reviewKeyboardAction(' ', { enrichmentOpen: false, typing: true, revealed: false })).toBeNull()
    expect(reviewKeyboardAction('3', { enrichmentOpen: true, typing: false, revealed: true })).toBeNull()
  })

  test('selection is inert until enrichment generation is explicit', () => {
    const selected = new Set(['EXAMPLE', 'COLLOCATIONS'] as const)

    expect(enrichmentFieldsForRequest(selected, false)).toBeNull()
    expect(enrichmentFieldsForRequest(selected, true)).toEqual(['EXAMPLE', 'COLLOCATIONS'])
    expect(enrichmentFieldsForRequest(new Set(), true)).toBeNull()
  })

  test('deck scopes never leak cards and All decks omits the query', () => {
    expect(cardMatchesDeck('IELTS', 'IELTS')).toBe(true)
    expect(cardMatchesDeck('Daily', 'IELTS')).toBe(false)
    expect(cardMatchesDeck(undefined, 'General')).toBe(true)
    expect(cardMatchesDeck('IELTS', 'General')).toBe(false)
    expect(cardMatchesDeck('HSK4', 'ALL')).toBe(true)
    expect(deckQuery('ALL')).toBeUndefined()
    expect(deckQuery('HSK4')).toBe('HSK4')
  })

  test('due state excludes future and sibling-buried directions', () => {
    const now = Date.parse('2026-07-12T10:00:00Z')

    expect(directionIsDue('2026-07-12T09:00:00Z', undefined, now)).toBe(true)
    expect(directionIsDue('2026-07-12T11:00:00Z', undefined, now)).toBe(false)
    expect(directionIsDue('2026-07-12T09:00:00Z', '2026-07-13T00:00:00Z', now)).toBe(false)
  })

  test('applied audio is reused only while its bound text is unchanged', () => {
    const metadata = {
      example: 'We met by chance.',
      frontAudioAssetId: 'word-audio',
      frontAudioText: 'serendipity',
      exampleAudioAssetId: 'example-audio',
      exampleAudioText: 'We met by chance.',
    }

    expect(wordAudioAssetId('serendipity', metadata)).toBe('word-audio')
    expect(wordAudioAssetId('fortuity', metadata)).toBeUndefined()
    expect(exampleAudioAssetId(metadata)).toBe('example-audio')
    expect(exampleAudioAssetId({ ...metadata, example: 'Changed.' })).toBeUndefined()
  })

  test('shadowing and dictation use the target-language example with safe fallbacks', () => {
    const metadata = { example: 'We met by pure chance. — Chúng tôi tình cờ gặp nhau.' }

    expect(examplePracticeText(metadata)).toBe('We met by pure chance.')
    expect(audioPracticeReference('serendipity', metadata, 'WORD')).toBe('serendipity')
    expect(audioPracticeReference('serendipity', metadata, 'SHADOWING')).toBe('We met by pure chance.')
    expect(audioPracticeReference('serendipity', metadata, 'DICTATION')).toBe('We met by pure chance.')
    expect(audioPracticeReference('serendipity', {}, 'SHADOWING')).toBe('')
    expect(audioPracticeReference('serendipity', {}, 'DICTATION')).toBe('serendipity')
  })

  test('shadowing requires connected speech in spaced or Han-script examples', () => {
    expect(shadowingPracticeReference({ example: 'Take it.' })).toBeUndefined()
    expect(shadowingPracticeReference({ example: 'Please take a seat.' })).toBe('Please take a seat.')
    expect(shadowingPracticeReference({ example: '我们明天一起去学校。 — Ngày mai chúng ta cùng đi học.' })).toBe('我们明天一起去学校。')
  })

  test('example audio binds to the target sentence rather than its translation', () => {
    expect(exampleAudioAssetId({
      example: 'We met by chance. — Chúng tôi tình cờ gặp nhau.',
      exampleAudioAssetId: 'example-audio',
      exampleAudioText: 'We met by chance.',
    })).toBe('example-audio')
  })

  test('audio trends never mix practice modes or provider scales', () => {
    const attempts = [
      { id: '1', mode: 'WORD', providerId: 'azure', accuracy: 90 },
      { id: '2', mode: 'SHADOWING', providerId: 'azure', accuracy: 80 },
      { id: '3', mode: 'WORD', providerId: 'other', accuracy: 70 },
      { id: '4', mode: 'WORD', providerId: 'azure', accuracy: 60 },
    ] as VocabAudioAttempt[]

    expect(comparableAudioTrendAttempts(attempts, 'WORD').map(({ id }) => id)).toEqual(['1', '4'])
    expect(comparableAudioTrendAttempts(attempts, 'SHADOWING').map(({ id }) => id)).toEqual(['2'])
  })

  test('dictation parsing is lenient and never exposes malformed JSON', () => {
    expect(parseDictationDiff('[{"kind":"MATCH","expected":"we","actual":"we"},{"kind":"MISSING","expected":"met","actual":null},{"kind":"SUBSTITUTION","expected":"pure","actual":"extra"}]')).toEqual([
      { kind: 'MATCH', expected: 'we', actual: 'we' },
      { kind: 'MISSING', expected: 'met', actual: undefined },
      { kind: 'SUBSTITUTION', expected: 'pure', actual: 'extra' },
    ])
    expect(parseDictationDiff('not-json')).toEqual([])
  })
})
