import { useQuery } from '@tanstack/react-query'
import { getHuggingNewsFeed, getHuggingNewsStory } from './hey-api'
import type {
  BriefDay,
  BriefEntity,
  BriefFeed,
  BriefPreviousStory,
  BriefSource,
  BriefStory,
  BriefStoryDetail,
  BriefTag,
  BriefTldrItem,
  BriefTopicCount,
} from './hey-api'
import { zGetHuggingNewsFeedResponse, zGetHuggingNewsStoryResponse } from './hey-api/zod.gen'
import { dataOrThrow } from './hey-api-result'

export type {
  BriefDay,
  BriefEntity,
  BriefFeed,
  BriefPreviousStory,
  BriefSource,
  BriefStory,
  BriefStoryDetail,
  BriefTag,
  BriefTldrItem,
  BriefTopicCount,
}

export function useHuggingNewsFeed() {
  return useQuery({
    queryKey: ['briefs', 'huggingnews'],
    queryFn: async (): Promise<BriefFeed> => zGetHuggingNewsFeedResponse
      .parse(dataOrThrow(await getHuggingNewsFeed())),
    staleTime: 5 * 60_000,
    retry: 1,
  })
}

export function useHuggingNewsStory(topic: string | undefined, slug: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['briefs', 'huggingnews', topic, slug],
    queryFn: async (): Promise<BriefStoryDetail> => zGetHuggingNewsStoryResponse.parse(dataOrThrow(
      await getHuggingNewsStory({ path: { topic: topic!, slug: slug! } }),
    )),
    enabled: enabled && Boolean(topic && slug),
    staleTime: 30 * 60_000,
    retry: 1,
  })
}

export function huggingNewsUrl(topic: string, slug: string) {
  return `https://huggingnews.com/${topic}/${slug}`
}
