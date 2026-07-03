import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { NoteDetail, NoteInput, NoteSummary, NoteUpdate } from './notes-types'

/** First page of notes, newest-first. 500 covers a personal KB; page further when needed. */
export async function listNotes(): Promise<NoteSummary[]> {
  const { data, error } = await api.GET('/api/notes', { params: { query: { page: 0, size: 500 } } })
  if (error) throw error
  return (data?.content ?? []) as NoteSummary[]
}

export async function searchNotes(query: string): Promise<NoteSummary[]> {
  const { data, error } = await api.GET('/api/notes/search', { params: { query: { q: query } } })
  if (error) throw error
  return (data ?? []) as NoteSummary[]
}

export async function getNote(slug: string): Promise<NoteDetail> {
  const { data, error } = await api.GET('/api/notes/{slug}', { params: { path: { slug } } })
  if (error) throw error
  return data as NoteDetail
}

export async function createNote(body: NoteInput): Promise<NoteDetail> {
  const { data, error } = await api.POST('/api/notes', { body })
  if (error) throw error
  return data as NoteDetail
}

export async function updateNote(id: string, body: NoteUpdate): Promise<NoteDetail> {
  const { data, error } = await api.PUT('/api/notes/{id}', { params: { path: { id } }, body })
  if (error) throw error
  return data as NoteDetail
}

/** Lists notes, or runs keyword search when {@code search} is non-empty. */
export function useNotes(search: string) {
  const query = search.trim()
  return useQuery({
    queryKey: ['notes', query],
    queryFn: () => (query ? searchNotes(query) : listNotes()),
  })
}

export function useNote(slug: string | undefined) {
  return useQuery({
    queryKey: ['note', slug],
    queryFn: () => getNote(slug as string),
    enabled: Boolean(slug),
  })
}

export function useCreateNote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createNote,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notes'] }),
  })
}

export function useUpdateNote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: NoteUpdate }) => updateNote(id, body),
    onSuccess: (note) => {
      // The title may have changed the slug, so seed the new slug's cache and refresh lists.
      queryClient.setQueryData(['note', note.slug], note)
      queryClient.invalidateQueries({ queryKey: ['notes'] })
      queryClient.invalidateQueries({ queryKey: ['note', note.slug] })
    },
  })
}
