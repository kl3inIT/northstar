import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { components } from './api.gen'

type Schemas = components['schemas']

export type Discipline = {
  id: string
  name: string
  color: Schemas['DisciplineSummary']['color']
}

export async function listDisciplines(): Promise<Discipline[]> {
  const { data, error } = await api.GET('/api/disciplines')
  if (error) throw error
  return (data ?? []).map((d) => ({ id: d.id, name: d.name, color: d.color }))
}

/** Disciplines change rarely — cache aggressively for the picker. */
export function useDisciplines() {
  return useQuery({ queryKey: ['disciplines'], queryFn: listDisciplines, staleTime: 5 * 60_000 })
}
