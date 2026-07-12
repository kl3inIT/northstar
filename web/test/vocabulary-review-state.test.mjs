import assert from 'node:assert/strict'
import test from 'node:test'
import {
  EMPTY_TALLY,
  cardMatchesDeck,
  deckQuery,
  enrichmentFieldsForRequest,
  incrementRating,
  reviewIsComplete,
  reviewKeyboardAction,
} from '../src/features/study/vocabulary-review-state.ts'

test('review stays on the front until reveal and completes after the last rating', () => {
  assert.equal(reviewIsComplete(0, 1), false)
  const tally = incrementRating(EMPTY_TALLY, 'GOOD')
  assert.deepEqual(tally, { AGAIN: 0, HARD: 0, GOOD: 1, EASY: 0 })
  assert.equal(reviewIsComplete(1, 1), true)
})

test('keyboard shortcuts respect reveal, typing, and enrichment states', () => {
  assert.deepEqual(reviewKeyboardAction(' ', { enrichmentOpen: false, typing: false, revealed: false }), { type: 'reveal' })
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
