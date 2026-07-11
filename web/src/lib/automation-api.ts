import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createAutomation,
  deleteAutomation,
  listAutomationRuns,
  listAutomations,
  listAutomationTypes,
  runAutomationNow,
  updateAutomation,
} from './hey-api'
import type {
  AutomationDefinitionSummary,
  AutomationRunSummary,
  AutomationTypeDescriptor,
  CreateAutomationRequest,
  UpdateAutomationRequest,
} from './hey-api'
import {
  zListAutomationRunsResponse,
  zListAutomationsResponse,
  zListAutomationTypesResponse,
} from './hey-api/zod.gen'
import { dataOrThrow, voidOrThrow } from './hey-api-result'

export type AutomationDefinition = AutomationDefinitionSummary
export type AutomationRun = AutomationRunSummary
export type AutomationType = AutomationTypeDescriptor
export type AutomationCreateInput = CreateAutomationRequest
export type AutomationUpdateInput = UpdateAutomationRequest

const automationsKey = ['settings', 'automations'] as const
const automationTypesKey = [...automationsKey, 'types'] as const

export function useAutomationTypes() {
  return useQuery({
    queryKey: automationTypesKey,
    staleTime: Number.POSITIVE_INFINITY,
    queryFn: async (): Promise<AutomationType[]> => zListAutomationTypesResponse
      .parse(dataOrThrow(await listAutomationTypes())),
  })
}

export function useAutomations() {
  return useQuery({
    queryKey: automationsKey,
    queryFn: async (): Promise<AutomationDefinition[]> => zListAutomationsResponse
      .parse(dataOrThrow(await listAutomations()))
      .map((definition) => ({
        ...definition,
        scheduleVersion: Number(definition.scheduleVersion),
        syncedScheduleVersion: Number(definition.syncedScheduleVersion),
        version: Number(definition.version),
      })),
  })
}

export function useAutomationRuns(id: string | null, enabled: boolean) {
  return useQuery({
    queryKey: [...automationsKey, id, 'runs'],
    enabled: Boolean(id) && enabled,
    queryFn: async () => zListAutomationRunsResponse.parse(dataOrThrow(await listAutomationRuns({
      path: { id: id! },
      query: { limit: 5 },
    }))),
  })
}

export function useCreateAutomation() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (body: AutomationCreateInput) => dataOrThrow(await createAutomation({ body })),
    onSuccess: () => client.invalidateQueries({ queryKey: automationsKey }),
  })
}

export function useUpdateAutomation() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, body }: { id: string; body: AutomationUpdateInput }) =>
      dataOrThrow(await updateAutomation({ path: { id }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: automationsKey }),
  })
}

export function useDeleteAutomation() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, version }: { id: string; version: number }) =>
      voidOrThrow(await deleteAutomation({ path: { id }, query: { version } })),
    onSuccess: async (_data, { id }) => {
      const runsKey = [...automationsKey, id, 'runs']
      await client.cancelQueries({ queryKey: runsKey, exact: true })
      client.removeQueries({ queryKey: runsKey, exact: true })
      await client.invalidateQueries({ queryKey: automationsKey, exact: true })
    },
  })
}

export function useRunAutomationNow() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => dataOrThrow(await runAutomationNow({ path: { id } })),
    onSuccess: async (run) => {
      await Promise.all([
        client.invalidateQueries({ queryKey: automationsKey }),
        client.invalidateQueries({ queryKey: [...automationsKey, run.automationId, 'runs'] }),
      ])
    },
  })
}
