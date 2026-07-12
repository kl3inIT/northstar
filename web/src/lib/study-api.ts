import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  assessSpeakingAttempt,
  assessVocabPronunciation,
  checkVocabAnswer,
  deleteSpeakingFeedback,
  deleteStudySession,
  deleteVocabCard,
  deleteWritingFeedback,
  generateSpeakingQuestion,
  getStudySummary,
  listMockResults,
  listSpeakingFeedback,
  listStudySessions,
  listStudySkills,
  listVocabCards,
  listVocabReviewCards,
  listWritingFeedback,
  previewVocabEnrichment,
  recordVocabReview,
  recordStudySessions,
  updateStudySession,
  updateVocabCard,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'
import type {
  PronunciationResult,
  SpeakingAttemptResult,
  SpeakingFeedbackSummary,
  StudyItemRequest,
  StudySessionSummary,
  StudySummary,
  UpdateVocabCardRequest,
  VocabAnswerAssessment,
  VocabCardSummary,
  VocabEnrichmentPreview,
  VocabEnrichmentRequest,
  VocabReviewRequest,
  WritingFeedbackSummary,
} from './hey-api'

export type StudySession = StudySessionSummary
export type StudyKind = StudySessionSummary['kind']
export type StudySessionInput = StudyItemRequest
export type VocabCard = VocabCardSummary
export type WritingFeedback = WritingFeedbackSummary
export type SpeakingFeedback = SpeakingFeedbackSummary
export type SpeakingAttempt = SpeakingAttemptResult
export type PronunciationAssessment = PronunciationResult
export type VocabAnswerCheck = VocabAnswerAssessment
export type VocabEnrichment = VocabEnrichmentPreview
export type VocabEnrichmentField = VocabEnrichmentRequest['fields'][number]
export type VocabRating = VocabReviewRequest['rating']
export type { StudySummary }

export interface WritingCriterion {
  key: string
  band: number
  justification: string
}

export interface WritingError {
  label: string
  quote: string
  fix: string
}

export interface SpeakingContentScores {
  vocabulary: number
  grammar: number
  topic: number
}

export interface SpeakingBandCriterion {
  key: 'FC' | 'LR' | 'GRA' | 'P'
  minBand: number
  maxBand: number
  confidence: 'LOW' | 'MEDIUM'
  evidenceQuote: string
  justification: string
}

export interface SpeakingIeltsEstimate {
  criteria: SpeakingBandCriterion[]
  overallMin: number
  overallMax: number
  confidence: 'LOW'
  label: string
}

export function parseSpeakingContentScores(value: string | undefined | null): SpeakingContentScores | null {
  if (!value) return null
  try {
    const parsed = JSON.parse(value) as Record<string, unknown>
    if (typeof parsed.vocabulary !== 'number' || typeof parsed.grammar !== 'number' || typeof parsed.topic !== 'number') {
      return null
    }
    return { vocabulary: parsed.vocabulary, grammar: parsed.grammar, topic: parsed.topic }
  } catch {
    return null
  }
}

/** Parse the persisted one-answer estimate; legacy and malformed rows deliberately return null. */
export function parseSpeakingIeltsEstimate(value: string | undefined | null): SpeakingIeltsEstimate | null {
  if (!value) return null
  try {
    const parsed = JSON.parse(value) as Record<string, unknown>
    if (!Array.isArray(parsed.criteria)
      || typeof parsed.overallMin !== 'number'
      || typeof parsed.overallMax !== 'number'
      || parsed.confidence !== 'LOW'
      || typeof parsed.label !== 'string') return null

    const allowedKeys = new Set(['FC', 'LR', 'GRA', 'P'])
    const criteria = parsed.criteria.filter((criterion): criterion is SpeakingBandCriterion => {
      if (typeof criterion !== 'object' || criterion === null) return false
      const candidate = criterion as Record<string, unknown>
      return typeof candidate.key === 'string'
        && allowedKeys.has(candidate.key)
        && typeof candidate.minBand === 'number'
        && typeof candidate.maxBand === 'number'
        && (candidate.confidence === 'LOW' || candidate.confidence === 'MEDIUM')
        && typeof candidate.evidenceQuote === 'string'
        && typeof candidate.justification === 'string'
    })
    if (criteria.length !== 4 || new Set(criteria.map((criterion) => criterion.key)).size !== 4) return null
    return {
      criteria,
      overallMin: parsed.overallMin,
      overallMax: parsed.overallMax,
      confidence: parsed.confidence,
      label: parsed.label,
    }
  } catch {
    return null
  }
}

/** The feedback's criteria JSON parsed leniently — malformed entries are dropped. */
export function parseWritingCriteria(criteria: string | undefined | null): WritingCriterion[] {
  if (!criteria) return []
  try {
    const parsed = JSON.parse(criteria) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (c): c is WritingCriterion =>
        typeof c === 'object' && c !== null
        && typeof (c as WritingCriterion).key === 'string'
        && typeof (c as WritingCriterion).band === 'number'
        && typeof (c as WritingCriterion).justification === 'string',
    )
  } catch {
    return []
  }
}

/** The feedback's topErrors JSON parsed leniently — malformed entries are dropped. */
export function parseWritingErrors(topErrors: string | undefined | null): WritingError[] {
  if (!topErrors) return []
  try {
    const parsed = JSON.parse(topErrors) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (e): e is WritingError =>
        typeof e === 'object' && e !== null
        && typeof (e as WritingError).label === 'string'
        && typeof (e as WritingError).quote === 'string'
        && typeof (e as WritingError).fix === 'string',
    )
  } catch {
    return []
  }
}

export interface VocabMetadata {
  reading?: string
  partOfSpeech?: string
  example?: string
  collocations?: string[]
  synonyms?: string[]
  antonyms?: string[]
  contrast?: string
  mnemonic?: string
}

function stringList(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined
  const values = value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
  return values.length > 0 ? values : undefined
}

/** The card's metadata JSON parsed leniently — unknown keys are ignored by the reader. */
export function parseVocabMetadata(metadata: string | undefined | null): VocabMetadata {
  if (!metadata) return {}
  try {
    const parsed = JSON.parse(metadata) as Record<string, unknown>
    return {
      reading: typeof parsed.reading === 'string' ? parsed.reading : undefined,
      partOfSpeech: typeof parsed.partOfSpeech === 'string' ? parsed.partOfSpeech : undefined,
      example: typeof parsed.example === 'string' ? parsed.example : undefined,
      collocations: stringList(parsed.collocations),
      synonyms: stringList(parsed.synonyms),
      antonyms: stringList(parsed.antonyms),
      contrast: typeof parsed.contrast === 'string' ? parsed.contrast : undefined,
      mnemonic: typeof parsed.mnemonic === 'string' ? parsed.mnemonic : undefined,
    }
  } catch {
    return {}
  }
}

/** Merge edited fields while preserving enrichment and future metadata keys. */
export function mergeVocabMetadata(
  metadata: string | undefined | null,
  updates: Record<string, string | string[] | undefined>,
): string | undefined {
  let merged: Record<string, unknown> = {}
  if (metadata) {
    try {
      const parsed = JSON.parse(metadata) as unknown
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        merged = { ...(parsed as Record<string, unknown>) }
      }
    } catch {
      // An edit repairs malformed legacy metadata.
    }
  }
  Object.entries(updates).forEach(([key, value]) => {
    if (value == null || (typeof value === 'string' && value.trim().length === 0)
      || (Array.isArray(value) && value.length === 0)) {
      delete merged[key]
    } else {
      merged[key] = typeof value === 'string' ? value.trim() : value
    }
  })
  return Object.keys(merged).length > 0 ? JSON.stringify(merged) : undefined
}

const tzHeaders = { 'X-Timezone': Intl.DateTimeFormat().resolvedOptions().timeZone }

/** The log for a date window (yyyy-MM-dd, inclusive), newest first. */
export function useStudySessions(from: string, to: string) {
  return useQuery({
    queryKey: ['study', from, to],
    queryFn: async () =>
      dataOrThrow(await listStudySessions({ query: { from, to }, headers: tzHeaders })),
  })
}

/** This ISO week vs last — the stat strip's numbers. */
export function useStudySummary() {
  return useQuery({
    queryKey: ['study-summary'],
    queryFn: async () => dataOrThrow(await getStudySummary({ headers: tzHeaders })),
  })
}

/** Scored mock tests oldest-first — the progress trend. */
export function useMockResults() {
  return useQuery({
    queryKey: ['study-mocks'],
    queryFn: async () => dataOrThrow(await listMockResults()),
  })
}

/** The constrained skill vocabulary (seed ∪ used) for the row editor's select. */
export function useStudySkills() {
  return useQuery({
    queryKey: ['study-skills'],
    staleTime: 5 * 60 * 1000,
    queryFn: async () => dataOrThrow(await listStudySkills()),
  })
}

/** Every card, newest first, with recall probability computed server-side for now. */
export function useVocabCards() {
  return useQuery({
    queryKey: ['study-vocab'],
    queryFn: async () => dataOrThrow(await listVocabCards()),
  })
}

export function useVocabReviewCards(limit: number, enabled: boolean) {
  return useQuery({
    queryKey: ['study-vocab-review', limit],
    enabled,
    queryFn: async () => dataOrThrow(await listVocabReviewCards({ query: { limit } })),
  })
}

export function useRecordVocabReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, rating }: { id: string; rating: VocabRating }) =>
      dataOrThrow(await recordVocabReview({ path: { id }, body: { rating } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['study-vocab'] })
      void queryClient.invalidateQueries({ queryKey: ['study-vocab-review'] })
    },
  })
}

export function useCheckVocabAnswer() {
  return useMutation({
    mutationFn: async ({ id, answer }: { id: string; answer: string }) =>
      dataOrThrow(await checkVocabAnswer({ path: { id }, body: { answer } })),
  })
}

export function usePreviewVocabEnrichment() {
  return useMutation({
    mutationFn: async ({ id, fields }: { id: string; fields: VocabEnrichmentField[] }) =>
      dataOrThrow(await previewVocabEnrichment({ path: { id }, body: { fields } })),
  })
}

export function useUpdateVocabCard() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, ...body }: UpdateVocabCardRequest & { id: string }) =>
      dataOrThrow(await updateVocabCard({ path: { id }, body })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['study-vocab'] }),
  })
}

export function useDeleteVocabCard() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      voidOrThrow(await deleteVocabCard({ path: { id } }))
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['study-vocab'] }),
  })
}

export function useAssessVocabPronunciation() {
  return useMutation({
    mutationFn: async ({ id, audio }: { id: string; audio: Blob }) =>
      dataOrThrow(await assessVocabPronunciation({ path: { id }, body: { audio } })),
  })
}

/** Every graded essay, newest first. Grading happens in chat, never here. */
export function useWritingFeedback() {
  return useQuery({
    queryKey: ['study-writing'],
    queryFn: async () => dataOrThrow(await listWritingFeedback()),
  })
}

export function useDeleteWritingFeedback() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      voidOrThrow(await deleteWritingFeedback({ path: { id } }))
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['study-writing'] }),
  })
}

export function useSpeakingFeedback() {
  return useQuery({
    queryKey: ['study-speaking'],
    queryFn: async () => dataOrThrow(await listSpeakingFeedback()),
  })
}

export function useGenerateSpeakingQuestion() {
  return useMutation({
    mutationFn: async (part: 1 | 2 | 3) =>
      dataOrThrow(await generateSpeakingQuestion({ body: { part } })),
  })
}

export function useAssessSpeakingAttempt() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ question, audio }: { question: string; audio: Blob }) =>
      dataOrThrow(await assessSpeakingAttempt({ body: { question, audio } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['study-speaking'] })
      void queryClient.invalidateQueries({ queryKey: ['study'] })
      void queryClient.invalidateQueries({ queryKey: ['study-summary'] })
    },
  })
}

export function useDeleteSpeakingFeedback() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      voidOrThrow(await deleteSpeakingFeedback({ path: { id } }))
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['study-speaking'] }),
  })
}

function useInvalidateStudy() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['study'] })
    void queryClient.invalidateQueries({ queryKey: ['study-summary'] })
    void queryClient.invalidateQueries({ queryKey: ['study-mocks'] })
    void queryClient.invalidateQueries({ queryKey: ['study-skills'] })
    void queryClient.invalidateQueries({ queryKey: ['study-vocab'] })
  }
}

/** Save a confirmed capture batch — every activity from one message in one call. */
export function useRecordStudySessions() {
  const invalidate = useInvalidateStudy()
  return useMutation({
    mutationFn: async (items: StudySessionInput[]) =>
      dataOrThrow(await recordStudySessions({ body: { items } })),
    onSuccess: invalidate,
  })
}

export function useUpdateStudySession() {
  const invalidate = useInvalidateStudy()
  return useMutation({
    mutationFn: async ({ id, ...body }: StudySessionInput & { id: string }) =>
      dataOrThrow(await updateStudySession({ path: { id }, body })),
    onSuccess: invalidate,
  })
}

export function useDeleteStudySession() {
  const invalidate = useInvalidateStudy()
  return useMutation({
    mutationFn: async (id: string) => {
      voidOrThrow(await deleteStudySession({ path: { id } }))
    },
    onSuccess: invalidate,
  })
}
