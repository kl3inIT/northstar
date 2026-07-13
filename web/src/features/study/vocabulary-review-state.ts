import type { VocabAudioAttempt, VocabCard, VocabEnrichmentField, VocabMetadata, VocabRating } from '@/lib/study-api'

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

export function directionIsDue(
  dueAt: string | undefined,
  buriedUntil: string | undefined,
  now: number,
): boolean {
  if (!dueAt) return false
  return new Date(dueAt).getTime() <= now
    && (!buriedUntil || new Date(buriedUntil).getTime() <= now)
}

export function cardHasDueDirection(card: VocabCard, now: number): boolean {
  return directionIsDue(card.dueAt, card.buriedUntil, now)
    || (card.productionEnabled
      && directionIsDue(card.productionDueAt, card.productionBuriedUntil, now))
}

/** Selection alone is inert; only an explicit Generate action returns request fields. */
export function enrichmentFieldsForRequest(
  selected: ReadonlySet<VocabEnrichmentField>,
  explicitlyRequested: boolean,
): VocabEnrichmentField[] | null {
  return explicitlyRequested && selected.size > 0 ? [...selected] : null
}

/** Generated audio is valid only for the exact text that was explicitly applied. */
export function wordAudioAssetId(front: string, metadata: VocabMetadata): string | undefined {
  return metadata.frontAudioAssetId && metadata.frontAudioText === front
    ? metadata.frontAudioAssetId
    : undefined
}

/** Example audio is invalidated automatically when the example text changes. */
export function exampleAudioAssetId(metadata: VocabMetadata): string | undefined {
  return metadata.exampleAudioAssetId && metadata.exampleAudioText === metadata.example
    ? metadata.exampleAudioAssetId
    : undefined
}

export function audioPracticeReference(
  front: string,
  metadata: VocabMetadata,
  mode: 'WORD' | 'SHADOWING' | 'DICTATION',
): string {
  return mode === 'WORD' ? front : metadata.example?.trim() || front
}

/** Trend values are comparable only inside one practice mode and one provider scale. */
export function comparableAudioTrendAttempts(
  attempts: VocabAudioAttempt[],
  mode: VocabAudioAttempt['mode'],
): VocabAudioAttempt[] {
  const matchingMode = attempts.filter((attempt) => attempt.mode === mode)
  const providerId = matchingMode[0]?.providerId
  return matchingMode.filter((attempt) => attempt.providerId === providerId)
}

export interface DictationDiffToken {
  kind: 'MATCH' | 'MISSING' | 'EXTRA' | 'SUBSTITUTION'
  expected?: string
  actual?: string
}

export function parseDictationDiff(value: string | undefined): DictationDiffToken[] {
  if (!value) return []
  try {
    const parsed = JSON.parse(value) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed.filter((token): token is DictationDiffToken => {
      if (typeof token !== 'object' || token === null) return false
      const candidate = token as Record<string, unknown>
      return (candidate.kind === 'MATCH' || candidate.kind === 'MISSING'
          || candidate.kind === 'EXTRA' || candidate.kind === 'SUBSTITUTION')
        && (candidate.expected === undefined || candidate.expected === null || typeof candidate.expected === 'string')
        && (candidate.actual === undefined || candidate.actual === null || typeof candidate.actual === 'string')
    }).map((token) => ({
      kind: token.kind,
      expected: token.expected || undefined,
      actual: token.actual || undefined,
    }))
  } catch {
    return []
  }
}
