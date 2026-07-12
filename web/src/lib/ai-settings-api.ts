import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createAiGateway,
  deleteAiGateway,
  getAiSettings,
  getAssistantConversationModel,
  listAiModels,
  listAiCapabilityTargets,
  resetAiRoute,
  testAiGateway,
  testAiGatewayDraft,
  updateAiGateway,
  updateAiRoute,
  updateAssistantConversationModel,
  type AiRoute as ApiAiRoute,
  type AiRouteSelection as ApiAiRouteSelection,
  type AiSettingsResponse,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'

export const AI_TASKS = [
  'ASSISTANT',
  'CAPTURE',
  'ALIGNMENT',
  'TITLE',
  'STUDY_GRADER',
  'IMAGE_CAPTION',
  'TEXT_TO_SPEECH',
  'SPEECH_TO_TEXT',
  'REALTIME_TRANSCRIPTION',
  'IMAGE_GENERATION',
  'EMBEDDING',
] as const

export type AiTask = (typeof AI_TASKS)[number]

export interface AiRoute {
  gatewayId: string
  modelId: string
  options?: Record<string, string>
}

export interface AiRouteSelection {
  route: AiRoute
  defaultRoute: AiRoute
  overridden: boolean
}

export interface AiGateway {
  id: string
  displayName: string
  type: AiGatewayType
  capabilities: AiGatewayCapability[]
  configured: boolean
  source: 'DEPLOYMENT' | 'SETTINGS'
  credentialSource: 'ENVIRONMENT' | 'SETTINGS' | 'NONE'
  deploymentBacked: boolean
  overridden: boolean
  editable: boolean
  baseUrl: string
  configuredModels: string[]
  configuredTtsTargets: string[]
  configuredWebSearchTargets: string[]
  configuredWebFetchTargets: string[]
  configuredSttTargets: string[]
  configuredImageTargets: string[]
  configuredEmbeddingTargets: string[]
  discoverModels: boolean
  timeoutSeconds: number
}

export type AiGatewayType = 'OPENAI' | 'NINE_ROUTER' | 'OPENAI_CHAT_COMPATIBLE'
export type AiGatewayCapability = 'CHAT' | 'WEB_SEARCH' | 'WEB_FETCH' | 'SPEECH_TO_TEXT' | 'TEXT_TO_SPEECH' | 'IMAGE_GENERATION' | 'EMBEDDING' | 'REALTIME'

export interface AiGatewayInput {
  id: string
  displayName: string
  type: AiGatewayType
  baseUrl: string
  apiKey?: string
  models: string[]
  ttsTargets: string[]
  webSearchTargets: string[]
  webFetchTargets: string[]
  sttTargets: string[]
  imageTargets: string[]
  embeddingTargets: string[]
  discoverModels: boolean
  timeoutSeconds: number
}

export interface AiGatewayConnectionTest {
  success: boolean
  latencyMillis: number
  models: AiModel[]
  ttsTargets: AiModel[]
  capabilityTargets: Partial<Record<AiGatewayCapability, AiModel[]>>
  message: string
}

export interface AiModel {
  gatewayId: string
  id: string
  displayName: string
}

export interface AiSettings {
  routes: Record<AiTask, AiRouteSelection>
  gateways: AiGateway[]
}

function route(value?: ApiAiRoute): AiRoute {
  if (!value?.gatewayId || !value.modelId) throw new Error('AI route is incomplete')
  return { gatewayId: value.gatewayId, modelId: value.modelId, options: value.options ?? {} }
}

function selection(value?: ApiAiRouteSelection): AiRouteSelection {
  if (!value) throw new Error('AI route selection is missing')
  return {
    route: route(value.route),
    defaultRoute: route(value.defaultRoute),
    overridden: value.overridden ?? false,
  }
}

function settings(value: AiSettingsResponse): AiSettings {
  const routes = Object.fromEntries(AI_TASKS.map((task) => [task, selection(value.routes?.[task])])) as Record<AiTask, AiRouteSelection>
  return {
    routes,
    gateways: (value.gateways ?? []).map((gateway) => {
      if (!gateway.id || !gateway.displayName) throw new Error('AI gateway identity is incomplete')
      return {
        id: gateway.id,
        displayName: gateway.displayName,
        type: gateway.type ?? 'OPENAI_CHAT_COMPATIBLE',
        capabilities: gateway.capabilities ?? ['CHAT'],
        configured: gateway.configured ?? false,
        source: gateway.source ?? 'DEPLOYMENT',
        credentialSource: gateway.credentialSource ?? 'NONE',
        deploymentBacked: gateway.deploymentBacked ?? false,
        overridden: gateway.overridden ?? false,
        editable: gateway.editable ?? false,
        baseUrl: gateway.baseUrl ?? '',
        configuredModels: gateway.configuredModels ?? [],
        configuredTtsTargets: gateway.configuredTtsTargets ?? [],
        configuredWebSearchTargets: gateway.configuredWebSearchTargets ?? [],
        configuredWebFetchTargets: gateway.configuredWebFetchTargets ?? [],
        configuredSttTargets: gateway.configuredSttTargets ?? [],
        configuredImageTargets: gateway.configuredImageTargets ?? [],
        configuredEmbeddingTargets: gateway.configuredEmbeddingTargets ?? [],
        discoverModels: gateway.discoverModels ?? false,
        timeoutSeconds: gateway.timeoutSeconds ?? 60,
      }
    }),
  }
}

function gateway(value: import('./hey-api').AiGatewayDescriptor): AiGateway {
  if (!value.id || !value.displayName) throw new Error('AI gateway identity is incomplete')
  return {
    id: value.id,
    displayName: value.displayName,
    type: value.type ?? 'OPENAI_CHAT_COMPATIBLE',
    capabilities: value.capabilities ?? ['CHAT'],
    configured: value.configured ?? false,
    source: value.source ?? 'DEPLOYMENT',
    credentialSource: value.credentialSource ?? 'NONE',
    deploymentBacked: value.deploymentBacked ?? false,
    overridden: value.overridden ?? false,
    editable: value.editable ?? false,
    baseUrl: value.baseUrl ?? '',
    configuredModels: value.configuredModels ?? [],
    configuredTtsTargets: value.configuredTtsTargets ?? [],
    configuredWebSearchTargets: value.configuredWebSearchTargets ?? [],
    configuredWebFetchTargets: value.configuredWebFetchTargets ?? [],
    configuredSttTargets: value.configuredSttTargets ?? [],
    configuredImageTargets: value.configuredImageTargets ?? [],
    configuredEmbeddingTargets: value.configuredEmbeddingTargets ?? [],
    discoverModels: value.discoverModels ?? false,
    timeoutSeconds: value.timeoutSeconds ?? 60,
  }
}

function connectionTest(value: import('./hey-api').AiGatewayTestResult): AiGatewayConnectionTest {
  return {
    success: value.success ?? false,
    latencyMillis: value.latencyMillis ?? 0,
    message: value.message ?? 'Connection test finished',
    models: (value.models ?? []).map((model) => {
      if (!model.gatewayId || !model.id || !model.displayName) throw new Error('AI model identity is incomplete')
      return { gatewayId: model.gatewayId, id: model.id, displayName: model.displayName }
    }),
    ttsTargets: (value.ttsTargets ?? []).map((target) => {
      if (!target.gatewayId || !target.id || !target.displayName) throw new Error('TTS target identity is incomplete')
      return { gatewayId: target.gatewayId, id: target.id, displayName: target.displayName }
    }),
    capabilityTargets: Object.fromEntries(Object.entries(value.capabilityTargets ?? {}).map(([capability, targets]) => [
      capability,
      (targets ?? []).map((target) => {
        if (!target.gatewayId || !target.id || !target.displayName) throw new Error('Capability target identity is incomplete')
        return { gatewayId: target.gatewayId, id: target.id, displayName: target.displayName }
      }),
    ])) as Partial<Record<AiGatewayCapability, AiModel[]>>,
  }
}

export function useAiSettings() {
  return useQuery({
    queryKey: ['settings', 'ai'],
    queryFn: async () => settings(dataOrThrow(await getAiSettings())),
  })
}

export function useAiModels(gatewayId?: string) {
  return useQuery({
    queryKey: ['settings', 'ai', 'models', gatewayId],
    enabled: Boolean(gatewayId),
    staleTime: 5 * 60_000,
    queryFn: () => fetchAiModels(gatewayId!),
  })
}

export function useAiCapabilityTargets(gatewayId?: string, capability?: AiGatewayCapability) {
  return useQuery({
    queryKey: ['settings', 'ai', 'targets', gatewayId, capability],
    enabled: Boolean(gatewayId && capability),
    staleTime: 5 * 60_000,
    queryFn: async () => dataOrThrow(await listAiCapabilityTargets({
      path: { gatewayId: gatewayId!, capability: capability! },
    })).map((target) => {
      if (!target.gatewayId || !target.id || !target.displayName) throw new Error('Capability target identity is incomplete')
      return { gatewayId: target.gatewayId, id: target.id, displayName: target.displayName }
    }),
  })
}

export function useChatAiModels() {
  const settingsQuery = useAiSettings()
  const gateways = (settingsQuery.data?.gateways ?? []).filter(
    (gateway) => gateway.configured && gateway.capabilities.includes('CHAT'),
  )
  const modelQueries = useQueries({
    queries: gateways.map((gateway) => ({
      queryKey: ['settings', 'ai', 'models', gateway.id],
      queryFn: () => fetchAiModels(gateway.id),
      staleTime: 5 * 60_000,
    })),
  })
  return {
    gateways,
    models: modelQueries.flatMap((query) => query.data ?? []),
    isLoading: settingsQuery.isLoading || modelQueries.some((query) => query.isLoading),
  }
}

async function fetchAiModels(gatewayId: string): Promise<AiModel[]> {
  return dataOrThrow(await listAiModels({ path: { gatewayId } })).map((model) => {
    if (!model.gatewayId || !model.id || !model.displayName) throw new Error('AI model identity is incomplete')
    return { gatewayId: model.gatewayId, id: model.id, displayName: model.displayName }
  })
}

export function useUpdateAiRoute() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ task, route: value }: { task: AiTask; route: AiRoute }) =>
      selection(dataOrThrow(await updateAiRoute({ path: { task }, body: value }))),
    onSuccess: (selection, variables) => queryClient.setQueryData<AiSettings>(
      ['settings', 'ai'],
      (current) => current && ({
        ...current,
        routes: { ...current.routes, [variables.task]: selection },
      }),
    ),
  })
}

export function useResetAiRoute() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (task: AiTask) =>
      selection(dataOrThrow(await resetAiRoute({ path: { task } }))),
    onSuccess: (selection, task) => queryClient.setQueryData<AiSettings>(
      ['settings', 'ai'],
      (current) => current && ({
        ...current,
        routes: { ...current.routes, [task]: selection },
      }),
    ),
  })
}

export function useSaveAiGateway() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ input, editing }: { input: AiGatewayInput; editing: boolean }) => gateway(dataOrThrow(
      editing
        ? await updateAiGateway({ path: { gatewayId: input.id }, body: input })
        : await createAiGateway({ body: input }),
    )),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['settings', 'ai'] })
      await queryClient.invalidateQueries({ queryKey: ['settings', 'ai', 'models'] })
    },
  })
}

export function useTestAiGatewayDraft() {
  return useMutation({
    mutationFn: async (input: AiGatewayInput) => connectionTest(dataOrThrow(
      await testAiGatewayDraft({ body: input }),
    )),
  })
}

export function useTestAiGateway() {
  return useMutation({
    mutationFn: async (gatewayId: string) => connectionTest(dataOrThrow(
      await testAiGateway({ path: { gatewayId } }),
    )),
  })
}

export function useDeleteAiGateway() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (gatewayId: string) => voidOrThrow(
      await deleteAiGateway({ path: { gatewayId } }),
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['settings', 'ai'] })
      await queryClient.invalidateQueries({ queryKey: ['settings', 'ai', 'models'] })
    },
  })
}

export function useAssistantConversationModel(conversationId: string) {
  return useQuery({
    queryKey: ['assistant-model', conversationId],
    queryFn: async () => route(dataOrThrow(await getAssistantConversationModel({ path: { id: conversationId } }))),
  })
}

export function useUpdateAssistantConversationModel(conversationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (value: AiRoute) => route(dataOrThrow(await updateAssistantConversationModel({
      path: { id: conversationId },
      body: value,
    }))),
    onSuccess: (route) => queryClient.setQueryData(['assistant-model', conversationId], route),
  })
}
