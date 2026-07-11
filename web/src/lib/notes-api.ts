import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createNote as createNoteRequest,
  getNote as getNoteRequest,
  listNotes as listNotesRequest,
  listNotesByProject,
  searchNotes as searchNotesRequest,
  setNoteStatus as setNoteStatusRequest,
  updateNote as updateNoteRequest,
} from './hey-api'
import { dataOrThrow } from './hey-api-result'
import type { NoteDetail, NoteInput, NoteStatus, NoteSummary, NoteUpdate } from './notes-types'

/**
 * First page of notes, newest-first; {@code status} narrows to one MFI
 * working-state tab (Staging / Resources / Archive). Bounded high enough for
 * the virtualized personal KB tree; API pagination/search remains the next
 * scale boundary.
 */
export async function listNotes(status?: NoteStatus): Promise<NoteSummary[]> {
  const data = dataOrThrow(await listNotesRequest({ query: { page: 0, size: 5000, status } }))
  return (data?.content ?? []) as NoteSummary[]
}

/** Just the total for a status tab — one row fetched, count read from the page metadata. */
export async function countNotes(status?: NoteStatus): Promise<number> {
  const data = dataOrThrow(await listNotesRequest({ query: { page: 0, size: 1, status } }))
  return data?.page?.totalElements ?? 0
}

export async function searchNotes(query: string): Promise<NoteSummary[]> {
  return dataOrThrow(await searchNotesRequest({ query: { q: query } })) as NoteSummary[]
}

export async function listProjectNotes(projectId: string): Promise<NoteSummary[]> {
  return dataOrThrow(await listNotesByProject({ query: { projectId } })) as NoteSummary[]
}

export async function getNote(slug: string): Promise<NoteDetail> {
  return dataOrThrow(await getNoteRequest({ path: { slug } })) as NoteDetail
}

export async function createNote(body: NoteInput): Promise<NoteDetail> {
  return dataOrThrow(await createNoteRequest({ body })) as NoteDetail
}

export async function updateNote(id: string, body: NoteUpdate): Promise<NoteDetail> {
  return dataOrThrow(await updateNoteRequest({ path: { id }, body })) as NoteDetail
}

export async function moveNoteToFolder(
  note: Pick<NoteSummary, 'id' | 'slug'>,
  folderPath: string,
): Promise<NoteDetail> {
  const current = await getNote(note.slug)
  return updateNote(note.id, {
    title: current.title,
    folderPath,
    contentMarkdown: current.contentMarkdown,
    tags: current.tags,
    projectId: current.projectId,
    version: current.version,
  })
}

/** Staging verdict ("→ Resources" / "Archive") or a restore. */
export async function setNoteStatus(id: string, status: NoteStatus): Promise<NoteDetail> {
  return dataOrThrow(await setNoteStatusRequest({
    path: { id },
    body: { status },
  })) as NoteDetail
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

/** All notes (every status), title + slug — for `[[` autocomplete and click-nav in the editor. */
export function useNoteIndex() {
  return useQuery({
    queryKey: ['notes', 'index'],
    queryFn: () => listNotes(),
    staleTime: 60_000,
  })
}

/** Morning Brief artifacts across all MFI states, newest issue first. */
export function useBriefNotes() {
  return useQuery({
    queryKey: ['notes', 'briefs'],
    queryFn: async () => (await listNotes())
      .filter((note) => note.folderPath === 'Briefs' && note.tags.includes('morning-brief'))
      .sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt)),
    staleTime: 30_000,
  })
}

/** Badge count for the staging review queue (sidebar + tab) — total only, not the rows. */
export function useStagingCount(enabled = true) {
  return useQuery({
    queryKey: ['notes', 'STAGING', 'count'],
    queryFn: () => countNotes('STAGING'),
    staleTime: 30_000,
    enabled,
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notes'] })
      queryClient.invalidateQueries({ queryKey: ['project-notes'] })
    },
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
      queryClient.invalidateQueries({ queryKey: ['project-notes'] })
    },
  })
}

export function useMoveNoteToFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ note, folderPath }: { note: Pick<NoteSummary, 'id' | 'slug'>; folderPath: string }) =>
      moveNoteToFolder(note, folderPath),
    onSuccess: (note) => {
      queryClient.setQueryData(['note', note.slug], note)
      queryClient.invalidateQueries({ queryKey: ['notes'] })
      queryClient.invalidateQueries({ queryKey: ['project-notes'] })
    },
  })
}

export function useProjectNotes(projectId: string | null | undefined) {
  return useQuery({
    queryKey: ['project-notes', projectId],
    enabled: Boolean(projectId),
    queryFn: () => listProjectNotes(projectId as string),
  })
}
