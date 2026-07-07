import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { NoteDetail, NoteInput, NoteStatus, NoteSummary, NoteUpdate } from './notes-types'

/**
 * First page of notes, newest-first; {@code status} narrows to one MFI
 * working-state tab (Staging / Resources / Archive). 500 covers a personal KB.
 */
export async function listNotes(status?: NoteStatus): Promise<NoteSummary[]> {
  const { data, error } = await api.GET('/api/notes', {
    params: { query: { page: 0, size: 500, status } },
  })
  if (error) throw error
  return (data?.content ?? []) as NoteSummary[]
}

/** Just the total for a status tab — one row fetched, count read from the page metadata. */
export async function countNotes(status?: NoteStatus): Promise<number> {
  const { data, error } = await api.GET('/api/notes', {
    params: { query: { page: 0, size: 1, status } },
  })
  if (error) throw error
  return data?.page?.totalElements ?? 0
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

/** Staging verdict ("→ Resources" / "Archive") or a restore. */
export async function setNoteStatus(id: string, status: NoteStatus): Promise<NoteDetail> {
  const { data, error } = await api.PATCH('/api/notes/{id}/status', {
    params: { path: { id } },
    body: { status },
  })
  if (error) throw error
  return data as NoteDetail
}

/** Lists RESOURCE notes (the trusted KB), or runs keyword search when {@code search} is non-empty. */
export function useNotes(search: string) {
  const query = search.trim()
  return useQuery({
    queryKey: ['notes', 'RESOURCE', query],
    queryFn: () => (query ? searchNotes(query) : listNotes('RESOURCE')),
  })
}

/** One working-state tab list (Staging review queue, Archive). */
export function useNotesByStatus(status: NoteStatus) {
  return useQuery({ queryKey: ['notes', status], queryFn: () => listNotes(status) })
}

/** Badge count for the staging review queue (sidebar + tab) — total only, not the rows. */
export function useStagingCount() {
  return useQuery({
    queryKey: ['notes', 'STAGING', 'count'],
    queryFn: () => countNotes('STAGING'),
    staleTime: 30_000,
  })
}

export function useSetNoteStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: NoteStatus }) => setNoteStatus(id, status),
    onSuccess: (note) => {
      queryClient.setQueryData(['note', note.slug], note)
      queryClient.invalidateQueries({ queryKey: ['notes'] })
      queryClient.invalidateQueries({ queryKey: ['note', note.slug] })
    },
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
