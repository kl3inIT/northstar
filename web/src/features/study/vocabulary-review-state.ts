import type { VocabEnrichmentField, VocabRating } from '@/lib/study-api'

export type RatingTally = Record<VocabRating, number>

export const EMPTY_TALLY: RatingTally = { AGAIN: 0, HARD: 0, GOOD: 0, EASY: 0 }

export type ReviewKeyboardAction =
  | { type: 'exit' }
  | { type: 'listen' }
  | { type: 'flip' }
  | { type: 'rate'; rating: VocabRating }

const SHORTCUT_RATINGS: VocabRating[] = ['AGAIN', 'HARD', 'GOOD', 'EASY']

export function reviewKeyboardAction(
  key: string,
  context: { enrichmentOpen: boolean; typing: boolean; revealed: boolean },
): ReviewKeyboardAction | null {
  if (context.enrichmentOpen) return null
  if (key === 'Escape') return { type: 'exit' }
  if (context.typing) return null
  if (key === ' ') return { type: 'flip' }
  if (key.toLowerCase() === 'r') return { type: 'listen' }
  if (context.revealed && /^[1-4]$/.test(key)) {
    return { type: 'rate', rating: SHORTCUT_RATINGS[Number(key) - 1] }
  }
  return null
}

export function incrementRating(tally: RatingTally, rating: VocabRating): RatingTally {
  return { ...tally, [rating]: tally[rating] + 1 }
}

export function reviewIsComplete(index: number, cardCount: number): boolean {
  return index >= cardCount
}

export function cardMatchesDeck(storedDeck: string | undefined, scope: string): boolean {
  if (scope === 'ALL') return true
  if (scope === 'General') return !storedDeck
  return storedDeck?.toLocaleLowerCase() === scope.toLocaleLowerCase()
}

export function deckQuery(scope: string): string | undefined {
  return scope === 'ALL' ? undefined : scope
}

/** Selection alone is inert; only an explicit Generate action returns request fields. */
export function enrichmentFieldsForRequest(
  selected: ReadonlySet<VocabEnrichmentField>,
  explicitlyRequested: boolean,
): VocabEnrichmentField[] | null {
  return explicitlyRequested && selected.size > 0 ? [...selected] : null
}
