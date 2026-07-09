import {
  deleteNote as deleteNoteRequest,
  draftCapture,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'
import type { CaptureDraft, CaptureRequest } from './hey-api'
import { createEvent, deleteEvent } from './calendar-api'
import { listDisciplines } from './disciplines-api'
import { createNote } from './notes-api'
import { createTask, deleteTask } from './tasks-api'

export type CaptureKind = 'TASK' | 'NOTE' | 'EVENT'

export type CaptureResult =
  | { kind: 'TASK'; id: string; title: string; dueDate: string | null; undo: () => Promise<void> }
  | { kind: 'NOTE'; id: string; title: string; slug: string; folderPath: string; undo: () => Promise<void> }
  | { kind: 'EVENT'; id: string; title: string; startAt: string; undo: () => Promise<void> }

/** "2026-07-07" + "14:00" in the browser's zone (what the user meant when speaking). */
function local(date: string, time: string): Date {
  return new Date(`${date}T${time}:00`)
}

/** The LLM names a discipline; resolve it to an id by exact name, else undefined (drop). */
async function resolveDisciplineId(name?: string | null): Promise<string | undefined> {
  if (!name) return undefined
  const disciplines = await listDisciplines().catch(() => [])
  return disciplines.find((d) => d.name === name)?.id
}

export async function deleteNote(id: string): Promise<void> {
  voidOrThrow(await deleteNoteRequest({ path: { id } }))
}

/**
 * Fire-and-forget capture: classify with one LLM call, then create the task or
 * note immediately. The caller shows a toast with the returned {@code undo}
 * (deletes what was just created) instead of a review dialog. Passing
 * {@code kind} (the "Thêm task"/"Thêm note" chips) skips classification.
 */
export async function capture(text: string, kind?: CaptureKind): Promise<CaptureResult> {
  const draft = dataOrThrow(await draftCapture({
    body: { text, kind } satisfies CaptureRequest,
  })) as CaptureDraft

  if (draft.kind === 'TASK' && draft.task) {
    const t = draft.task
    const disciplineId = await resolveDisciplineId(t.disciplineName)
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

  if (draft.kind === 'EVENT' && draft.event) {
    const ev = draft.event
    const disciplineId = await resolveDisciplineId(ev.disciplineName)
    const date = ev.date || new Date().toISOString().slice(0, 10)
    // Same local-time convention as the calendar dialogs: all-day = 00:00→23:59,
    // a timed event with no stated end gets a 1-hour default.
    const allDay = !ev.startTime
    const start = local(date, ev.startTime || '00:00')
    const end = ev.endTime
      ? local(date, ev.endTime)
      : allDay
        ? local(date, '23:59')
        : new Date(start.getTime() + 60 * 60 * 1000)
    const created = await createEvent({
      title: ev.title || text,
      notes: ev.notes || undefined,
      startAt: start.toISOString(),
      endAt: end.toISOString(),
      allDay,
      color: 'BLUE',
      disciplineId,
    })
    return {
      kind: 'EVENT',
      id: created.id,
      title: created.title,
      startAt: created.startDate,
      undo: () => deleteEvent(created.id),
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
