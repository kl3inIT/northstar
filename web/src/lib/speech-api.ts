import { useMutation, useQuery } from '@tanstack/react-query'
import {
  listSpeechTargets,
  synthesizeSpeech,
  type SpeechAssetResponse as ApiSpeechAssetResponse,
} from './hey-api'
import { dataOrThrow } from './hey-api-result'

export interface SpeechTarget {
  gatewayId: string
  id: string
  displayName: string
}

export interface SpeechAsset {
  id: string
  audioUrl: string
  gatewayId: string
  targetId: string
  mediaType: string
  cacheHit: boolean
}

export function useSpeechTargets(gatewayId?: string) {
  return useQuery({
    queryKey: ['settings', 'ai', 'speech-targets', gatewayId],
    enabled: Boolean(gatewayId),
    staleTime: 5 * 60_000,
    queryFn: async () => dataOrThrow(await listSpeechTargets({ path: { gatewayId: gatewayId! } }))
      .map((target) => {
        if (!target.gatewayId || !target.id || !target.displayName) {
          throw new Error('Speech target identity is incomplete')
        }
        return { gatewayId: target.gatewayId, id: target.id, displayName: target.displayName }
      }),
  })
}

export function useSynthesizeSpeech() {
  return useMutation({
    mutationFn: async (text: string) => speechAsset(dataOrThrow(await synthesizeSpeech({
      body: { text },
    }))),
  })
}

function speechAsset(value: ApiSpeechAssetResponse): SpeechAsset {
  if (!value.id || !value.audioUrl || !value.gatewayId || !value.targetId || !value.mediaType) {
    throw new Error('Speech asset response is incomplete')
  }
  return {
    id: value.id,
    audioUrl: value.audioUrl,
    gatewayId: value.gatewayId,
    targetId: value.targetId,
    mediaType: value.mediaType,
    cacheHit: value.cacheHit ?? false,
  }
}
