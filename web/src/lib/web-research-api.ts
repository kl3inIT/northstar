import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getWebResearchSettings,
  listWebResearchProviders,
  resetWebResearchSettings,
  updateWebResearchSettings,
} from './hey-api'
import { zListWebResearchProvidersResponse, zSettingsResponse } from './hey-api/zod.gen'
import { dataOrThrow } from './hey-api-result'
import type { AiGatewayType } from './ai-settings-api'

export interface WebResearchSettings {
  enabled: boolean
  searchProviderId: string
  searchRoute: WebProviderRoute
  pageReaderId: string
  pageReaderRoute: WebProviderRoute
  fallbackEnabled: boolean
  overridden: boolean
}

export interface WebProviderRoute {
  gatewayId: string
  targetId: string
}

export interface WebResearchProvider {
  id: string
  displayName: string
  capabilities: Array<'SEARCH' | 'READ_PAGE'>
  configured: boolean
  routeRequired: boolean
  gatewayTypes: AiGatewayType[]
}

export type WebResearchSettingsInput = Omit<WebResearchSettings, 'overridden'>

function settings(value: unknown): WebResearchSettings {
  const parsed = zSettingsResponse.parse(value)
  if (!parsed.searchProviderId || !parsed.pageReaderId) {
    throw new Error('Web research provider configuration is incomplete')
  }
  return {
    enabled: parsed.enabled ?? false,
    searchProviderId: parsed.searchProviderId,
    searchRoute: route(parsed.searchRoute),
    pageReaderId: parsed.pageReaderId,
    pageReaderRoute: route(parsed.pageReaderRoute),
    fallbackEnabled: parsed.fallbackEnabled ?? false,
    overridden: parsed.overridden ?? false,
  }
}

function providers(value: unknown): WebResearchProvider[] {
  return zListWebResearchProvidersResponse.parse(value).map((provider) => {
    if (!provider.id || !provider.displayName) throw new Error('A web provider is missing its identity')
    return {
      id: provider.id,
      displayName: provider.displayName,
      capabilities: provider.capabilities ?? [],
      configured: provider.configured ?? false,
      routeRequired: provider.routeRequired ?? false,
      gatewayTypes: provider.gatewayTypes ?? [],
    }
  })
}

function route(value?: { gatewayId?: string; targetId?: string }): WebProviderRoute {
  return { gatewayId: value?.gatewayId ?? '', targetId: value?.targetId ?? '' }
}

export function useWebResearchSettings() {
  return useQuery({
    queryKey: ['settings', 'web-research'],
    queryFn: async () => settings(dataOrThrow(await getWebResearchSettings())),
  })
}

export function useWebResearchProviders() {
  return useQuery({
    queryKey: ['settings', 'web-research', 'providers'],
    staleTime: 60_000,
    queryFn: async () => providers(dataOrThrow(await listWebResearchProviders())),
  })
}

export function useUpdateWebResearchSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: WebResearchSettingsInput) =>
      settings(dataOrThrow(await updateWebResearchSettings({ body }))),
    onSuccess: (value) => queryClient.setQueryData(['settings', 'web-research'], value),
  })
}

export function useResetWebResearchSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => settings(dataOrThrow(await resetWebResearchSettings())),
    onSuccess: (value) => queryClient.setQueryData(['settings', 'web-research'], value),
  })
}
