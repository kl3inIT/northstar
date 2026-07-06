import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { components } from './api.gen'

type Schemas = components['schemas']

export type Discipline = {
  id: string
  name: string
  color: Schemas['DisciplineSummary']['color']
}

export type DisciplineCard = Schemas['DisciplineCard']
export type DisciplineOverview = Schemas['DisciplineOverview']

// Upcoming-events windows on cards/overview follow the browser's zone.
const tzHeaders = { 'X-Timezone': Intl.DateTimeFormat().resolvedOptions().timeZone }

export async function listDisciplines(): Promise<Discipline[]> {
  const { data, error } = await api.GET('/api/disciplines')
  if (error) throw error
  return (data ?? []).map((d) => ({ id: d.id, name: d.name, color: d.color }))
}

/** Disciplines change rarely — cache aggressively for the picker. */
export function useDisciplines() {
  return useQuery({ queryKey: ['disciplines'], queryFn: listDisciplines, staleTime: 5 * 60_000 })
}

async function fetchCards(): Promise<DisciplineCard[]> {
  const { data, error } = await api.GET('/api/disciplines/cards', { headers: tzHeaders })
  if (error) throw error
  return data ?? []
}

/** The /disciplines grid: every discipline with its live counts. */
export function useDisciplineCards() {
  return useQuery({ queryKey: ['discipline-cards'], queryFn: fetchCards })
}

async function fetchOverview(id: string): Promise<DisciplineOverview> {
  const { data, error } = await api.GET('/api/disciplines/{id}/overview', {
    params: { path: { id } },
    headers: tzHeaders,
  })
  if (error) throw error
  return data as DisciplineOverview
}

/** The slice page: everything one discipline holds right now. */
export function useDisciplineOverview(id: string) {
  return useQuery({ queryKey: ['discipline-overview', id], queryFn: () => fetchOverview(id) })
}

export interface DisciplineInput {
  name: string
  color: Discipline['color']
}

async function createDiscipline(body: DisciplineInput): Promise<Discipline> {
  const { data, error } = await api.POST('/api/disciplines', { body })
  if (error) throw error
  return data as Discipline
}

async function updateDiscipline(id: string, body: DisciplineInput): Promise<Discipline> {
  const { data, error } = await api.PUT('/api/disciplines/{id}', {
    params: { path: { id } },
    body,
  })
  if (error) throw error
  return data as Discipline
}

function useInvalidateDisciplines() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['disciplines'] })
    queryClient.invalidateQueries({ queryKey: ['discipline-cards'] })
    queryClient.invalidateQueries({ queryKey: ['discipline-overview'] })
  }
}

export function useCreateDiscipline() {
  const invalidate = useInvalidateDisciplines()
  return useMutation({ mutationFn: createDiscipline, onSuccess: invalidate })
}

export function useUpdateDiscipline() {
  const invalidate = useInvalidateDisciplines()
  return useMutation({
    mutationFn: ({ id, ...body }: DisciplineInput & { id: string }) => updateDiscipline(id, body),
    onSuccess: invalidate,
  })
}
