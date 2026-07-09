import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listDisciplinesOptions,
  listDisciplinesQueryKey,
} from './hey-api/@tanstack/react-query.gen'
import {
  zCreateDisciplineBody,
  zCreateDisciplineResponse,
  zListDisciplinesResponse,
  zUpdateDisciplineBody,
  zUpdateDisciplineResponse,
} from './hey-api/zod.gen'
import {
  createDiscipline as createDisciplineRequest,
  deleteDiscipline as deleteDisciplineRequest,
  getDisciplineOverview,
  listDisciplineCards,
  listDisciplines as listDisciplinesRequest,
  updateDiscipline as updateDisciplineRequest,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'
import type { DisciplineCard, DisciplineOverview, DisciplineSummary } from './hey-api'

export type Discipline = {
  id: string
  name: string
  color: DisciplineSummary['color']
}

export type { DisciplineCard, DisciplineOverview }

// Upcoming-events windows on cards/overview follow the browser's zone.
const tzHeaders = { 'X-Timezone': Intl.DateTimeFormat().resolvedOptions().timeZone }

function toDiscipline(discipline: DisciplineSummary): Discipline {
  return {
    id: discipline.id,
    name: discipline.name,
    color: discipline.color,
  }
}

function toDisciplines(disciplines: DisciplineSummary[]): Discipline[] {
  return zListDisciplinesResponse.parse(disciplines).map(toDiscipline)
}

export async function listDisciplines(): Promise<Discipline[]> {
  const data = dataOrThrow(await listDisciplinesRequest())
  return toDisciplines(data)
}

/** Disciplines change rarely — cache aggressively for the picker. */
export function useDisciplines() {
  return useQuery({
    ...listDisciplinesOptions(),
    select: toDisciplines,
    staleTime: 5 * 60_000,
  })
}

async function fetchCards(): Promise<DisciplineCard[]> {
  return dataOrThrow(await listDisciplineCards({ headers: tzHeaders }))
}

/** The /disciplines grid: every discipline with its live counts. */
export function useDisciplineCards() {
  return useQuery({ queryKey: ['discipline-cards'], queryFn: fetchCards })
}

async function fetchOverview(id: string): Promise<DisciplineOverview> {
  return dataOrThrow(await getDisciplineOverview({
    path: { id },
    headers: tzHeaders,
  }))
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
  const parsedBody = zCreateDisciplineBody.parse(body)
  const data = dataOrThrow(await createDisciplineRequest({ body: parsedBody }))
  return toDiscipline(zCreateDisciplineResponse.parse(data))
}

async function updateDiscipline(id: string, body: DisciplineInput): Promise<Discipline> {
  const parsedBody = zUpdateDisciplineBody.parse(body)
  const data = dataOrThrow(await updateDisciplineRequest({
    path: { id },
    body: parsedBody,
  }))
  return toDiscipline(zUpdateDisciplineResponse.parse(data))
}

async function deleteDiscipline(id: string): Promise<void> {
  voidOrThrow(await deleteDisciplineRequest({ path: { id } }))
}

function useInvalidateDisciplines() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['disciplines'] })
    queryClient.invalidateQueries({ queryKey: listDisciplinesQueryKey() })
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

export function useDeleteDiscipline() {
  const invalidate = useInvalidateDisciplines()
  return useMutation({ mutationFn: deleteDiscipline, onSuccess: invalidate })
}
