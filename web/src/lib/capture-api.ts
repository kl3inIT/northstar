import { api } from './api'
import type { components } from './api.gen'
import { listDisciplines } from './disciplines-api'
import { createNote } from './notes-api'
import { createTask, deleteTask } from './tasks-api'

type Schemas = components['schemas']

export type CaptureKind = 'TASK' | 'NOTE'

export type CaptureResult =
  | { kind: 'TASK'; id: string; title: string; dueDate: string | null; undo: () => Promise<void> }
  | { kind: 'NOTE'; id: string; title: string; slug: string; folderPath: string; undo: () => Promise<void> }

export async function deleteNote(id: string): Promise<void> {
  const { error } = await api.DELETE('/api/notes/{id}', { params: { path: { id } } })
  if (error) throw error
}

/**
 * Voice capture: audio blob in, spoken text out (server-side Whisper; the
 * recording is never stored). Plain fetch because openapi-fetch needs a custom
 * serializer for multipart — not worth it for one endpoint.
 */
export async function transcribeAudio(blob: Blob): Promise<string> {
  const form = new FormData()
  form.append('audio', blob, 'capture.webm')
  const res = await fetch('/api/capture/transcribe', { method: 'POST', body: form })
  if (!res.ok) throw new Error(`transcribe failed: ${res.status}`)
  const data = (await res.json()) as { text: string }
  return data.text
}

/**
 * Fire-and-forget capture: classify with one LLM call, then create the task or
 * note immediately. The caller shows a toast with the returned {@code undo}
 * (deletes what was just created) instead of a review dialog. Passing
 * {@code kind} (the "Thêm task"/"Thêm note" chips) skips classification.
 */
export async function capture(text: string, kind?: CaptureKind): Promise<CaptureResult> {
  const { data, error } = await api.POST('/api/capture/draft', {
    body: { text, kind } satisfies Schemas['CaptureRequest'],
  })
  if (error) throw error
  const draft = data as Schemas['CaptureDraft']

  if (draft.kind === 'TASK' && draft.task) {
    const t = draft.task
    // The LLM names a discipline; resolve it to an id (exact name, else drop).
    let disciplineId: string | undefined
    if (t.disciplineName) {
      const disciplines = await listDisciplines().catch(() => [])
      disciplineId = disciplines.find((d) => d.name === t.disciplineName)?.id
    }
    // Strict structured output can hand back "" instead of null — drop empties
    // so TaskRequest's LocalDate/LocalTime parsing never sees them.
    const created = await createTask({
      title: t.title || text,
      notes: t.notes || undefined,
      dueDate: t.dueDate || undefined,
      dueTime: t.dueTime || undefined,
      disciplineId,
    })
    return {
      kind: 'TASK',
      id: created.id,
      title: created.title,
      dueDate: created.dueDate,
      undo: () => deleteTask(created.id),
    }
  }

  const n = draft.note ?? {}
  // Machine-drafted → STAGING: it waits in the Notes review queue, not the trusted KB.
  const created = await createNote({
    title: n.title ?? text.slice(0, 80),
    folderPath: n.folderPath ?? '',
    contentMarkdown: n.contentMarkdown ?? text,
    tags: n.tags ?? [],
    status: 'STAGING',
  })
  return {
    kind: 'NOTE',
    id: created.id,
    title: created.title,
    slug: created.slug,
    folderPath: created.folderPath,
    undo: () => deleteNote(created.id),
  }
}
