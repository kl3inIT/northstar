import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { NoteDetail } from './notes-types'

export type ReviewPeriod = 'daily' | 'weekly'

const tzHeaders = { 'X-Timezone': Intl.DateTimeFormat().resolvedOptions().timeZone }

/** Today's / this week's review note, or null when not generated yet (404). */
export async function getReview(period: ReviewPeriod): Promise<NoteDetail | null> {
  const { data, response, error } = await api.GET(`/api/alignment/${period}`, {
    headers: tzHeaders,
  })
  if (response.status === 404) return null
  if (error) throw error
  return data as NoteDetail
}

/** (Re)drafts the review — one LLM round-trip — and upserts the Journal note. */
export async function generateReview(period: ReviewPeriod): Promise<NoteDetail> {
  const { data, error } = await api.POST(`/api/alignment/${period}`, { headers: tzHeaders })
  if (error) throw error
  return data as NoteDetail
}

export function useReview(period: ReviewPeriod) {
  return useQuery({
    queryKey: ['alignment', period],
    queryFn: () => getReview(period),
    staleTime: 60_000,
  })
}

export function useGenerateReview(period: ReviewPeriod) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => generateReview(period),
    onSuccess: (note) => {
      queryClient.setQueryData(['alignment', period], note)
      // The review lives as a note, so the Notes tree/search must pick it up too.
      queryClient.setQueryData(['note', note.slug], note)
      queryClient.invalidateQueries({ queryKey: ['notes'] })
    },
  })
}
