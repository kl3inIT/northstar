import { useMutation } from '@tanstack/react-query'
import { api } from './api'
import type { components } from './api.gen'

type Schemas = components['schemas']

/** AI-suggested note draft — the user reviews it before creating the real note. */
export type NoteDraft = {
  title: string
  folderPath: string
  tags: string[]
  contentMarkdown: string
}

export async function draftNote(text: string): Promise<NoteDraft> {
  const { data, error } = await api.POST('/api/capture/draft', {
    body: { text } satisfies Schemas['CaptureRequest'],
  })
  if (error) throw error
  const draft = data as Schemas['NoteDraft']
  // The api's record always serializes every field; assert presence at the edge.
  return {
    title: draft.title ?? '',
    folderPath: draft.folderPath ?? '',
    tags: draft.tags ?? [],
    contentMarkdown: draft.contentMarkdown ?? '',
  }
}

export function useDraftNote() {
  return useMutation({ mutationFn: draftNote })
}
