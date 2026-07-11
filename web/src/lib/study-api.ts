import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  deleteStudySession,
  deleteVocabCard,
  getStudySummary,
  listMockResults,
  listStudySessions,
  listStudySkills,
  listVocabCards,
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
} from './hey-api'

export type StudySession = StudySessionSummary
export type StudyKind = StudySessionSummary['kind']
export type StudySessionInput = StudyItemRequest
export type VocabCard = VocabCardSummary
export type { StudySummary }

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
