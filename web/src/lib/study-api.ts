import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  deleteStudySession,
  deleteVocabCard,
  deleteWritingFeedback,
  getStudySummary,
  listMockResults,
  listStudySessions,
  listStudySkills,
  listVocabCards,
  listWritingFeedback,
  recordStudySessions,
  updateStudySession,
  updateVocabCard,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'
import type {
  StudyItemRequest,
  StudySessionSummary,
  StudySummary,
  UpdateVocabCardRequest,
  VocabCardSummary,
  WritingFeedbackSummary,
} from './hey-api'

export type StudySession = StudySessionSummary
export type StudyKind = StudySessionSummary['kind']
export type StudySessionInput = StudyItemRequest
export type VocabCard = VocabCardSummary
export type WritingFeedback = WritingFeedbackSummary
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

/** The card's metadata JSON parsed leniently — unknown keys are ignored. */
export function parseVocabMetadata(metadata: string | undefined | null): { reading?: string; example?: string } {
  if (!metadata) return {}
  try {
    const parsed = JSON.parse(metadata) as Record<string, unknown>
    return {
      reading: typeof parsed.reading === 'string' ? parsed.reading : undefined,
      example: typeof parsed.example === 'string' ? parsed.example : undefined,
    }
  } catch {
    return {}
  }
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

function useInvalidateStudy() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['study'] })
    queryClient.invalidateQueries({ queryKey: ['study-summary'] })
    queryClient.invalidateQueries({ queryKey: ['study-mocks'] })
    queryClient.invalidateQueries({ queryKey: ['study-skills'] })
    queryClient.invalidateQueries({ queryKey: ['study-vocab'] })
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
