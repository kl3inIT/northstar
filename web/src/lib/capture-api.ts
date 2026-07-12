import {
  deleteNote as deleteNoteRequest,
  deleteStudySession,
  deleteTransaction,
  deleteVocabCard,
  draftCapture,
  draftReceiptCapture,
  recordStudySessions,
  recordTransactions,
  recordVocabCards,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'
import type {
  CaptureDraft,
  CaptureRequest,
  ExpenseItem,
  StudyItem,
  StudySessionSummary,
  TransactionSummary,
  VocabCardSummary,
  VocabItem,
} from './hey-api'
import { createEvent, deleteEvent } from './calendar-api'
import { listDisciplines } from './disciplines-api'
import { createNote } from './notes-api'
import { createTask, deleteTask } from './tasks-api'

export type CaptureKind = 'TASK' | 'NOTE' | 'EVENT' | 'EXPENSE' | 'STUDY' | 'VOCAB'

export type CaptureResult =
  | { kind: 'TASK'; id: string; title: string; dueDate: string | null; undo: () => Promise<void> }
  | { kind: 'NOTE'; id: string; title: string; slug: string; folderPath: string; undo: () => Promise<void> }
  | { kind: 'EVENT'; id: string; title: string; startAt: string; undo: () => Promise<void> }
  | { kind: 'EXPENSE'; id: string; title: string; items: TransactionSummary[]; undo: () => Promise<void> }
  | { kind: 'STUDY'; id: string; title: string; items: StudySessionSummary[]; undo: () => Promise<void> }
  | { kind: 'VOCAB'; id: string; title: string; items: VocabCardSummary[]; undo: () => Promise<void> }

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

export async function deleteLedgerEntry(id: string): Promise<void> {
  voidOrThrow(await deleteTransaction({ path: { id } }))
}

export async function deleteStudyEntry(id: string): Promise<void> {
  voidOrThrow(await deleteStudySession({ path: { id } }))
}

export async function deleteVocabEntry(id: string): Promise<void> {
  voidOrThrow(await deleteVocabCard({ path: { id } }))
}

const VND = new Intl.NumberFormat('vi-VN')

/**
 * Save the draft's expense items as ledger rows and shape the echo the toast
 * shows — the echo (amount · category per item) is the moment the user ratifies
 * what the AI parsed, so it always names every amount and category.
 */
async function saveExpense(items: ExpenseItem[]): Promise<CaptureResult> {
  const today = new Date()
  const isoToday = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  const created = dataOrThrow(await recordTransactions({
    body: {
      items: items.map((i) => ({
        type: i.type ?? 'EXPENSE',
        amount: i.amount ?? 0,
        occurredOn: i.occurredOn || isoToday,
        description: i.description || 'expense',
        category: i.category || undefined,
        exceptional: i.exceptional ?? false,
      })),
    },
  })) as TransactionSummary[]
  const title = created
    .map((t) => `${t.description} · ${VND.format(t.amount)} ₫ · ${t.category}`)
    .join('  |  ')
  return {
    kind: 'EXPENSE',
    id: created[0]?.id ?? '',
    title,
    items: created,
    undo: async () => {
      await Promise.all(created.map((t) => deleteLedgerEntry(t.id)))
    },
  }
}

/**
 * Save the draft's study items as log entries with the same echo contract:
 * every activity is named back (skill · duration/score) so the user ratifies
 * what the AI parsed. Strict structured output writes "" for absent values —
 * drop those before they reach the API's integer/date fields.
 */
async function saveStudy(items: StudyItem[]): Promise<CaptureResult> {
  const today = new Date()
  const isoToday = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  const disciplineIds = await Promise.all(items.map((i) => resolveDisciplineId(i.disciplineName)))
  const created = dataOrThrow(await recordStudySessions({
    body: {
      items: items.map((i, index) => ({
        occurredOn: i.occurredOn || isoToday,
        skill: i.skill || 'Other',
        kind: i.kind === 'MOCK' ? 'MOCK' as const : 'PRACTICE' as const,
        durationMinutes: parsePositiveInt(i.durationMinutes),
        scoreRaw: parsePositiveInt(i.scoreRaw, true),
        scoreMax: parsePositiveInt(i.scoreMax),
        notes: i.notes || undefined,
        disciplineId: disciplineIds[index],
      })),
    },
  })) as StudySessionSummary[]
  const title = created
    .map((s) => {
      const parts = [s.skill]
      if (s.durationMinutes != null) parts.push(`${s.durationMinutes}m`)
      if (s.scoreRaw != null && s.scoreMax != null) parts.push(`${s.scoreRaw}/${s.scoreMax}`)
      return parts.join(' · ')
    })
    .join('  |  ')
  return {
    kind: 'STUDY',
    id: created[0]?.id ?? '',
    title,
    items: created,
    undo: async () => {
      await Promise.all(created.map((s) => deleteStudyEntry(s.id)))
    },
  }
}

function parsePositiveInt(value: string | undefined | null, allowZero = false): number | undefined {
  if (!value || !value.trim()) return undefined
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || (allowZero ? parsed < 0 : parsed <= 0)) return undefined
  return parsed
}

/**
 * Save the draft's vocab cards with the same echo contract — every card named
 * back (front · back) so the user ratifies what the AI parsed. Reading and
 * part of speech are the base language fields; generated enrichment is a
 * separate, explicit workflow.
 */
async function saveVocab(items: VocabItem[]): Promise<CaptureResult> {
  const disciplineIds = await Promise.all(items.map((i) => resolveDisciplineId(i.disciplineName)))
  const created = dataOrThrow(await recordVocabCards({
    body: {
      items: items.map((i, index) => {
        const metadata: Record<string, string> = {}
        if (i.reading?.trim()) metadata.reading = i.reading.trim()
        if (i.partOfSpeech?.trim()) metadata.partOfSpeech = i.partOfSpeech.trim()
        if (i.example?.trim()) metadata.example = i.example.trim()
        return {
          front: i.front || '(word)',
          back: i.back || '(meaning)',
          metadata: Object.keys(metadata).length > 0 ? JSON.stringify(metadata) : undefined,
          language: i.language ?? (/\p{Script=Han}/u.test(i.front ?? '') ? 'CHINESE' : 'ENGLISH'),
          deck: i.deck?.trim() || undefined,
          disciplineId: disciplineIds[index],
        }
      }),
    },
  })) as VocabCardSummary[]
  const title = created.map((c) => `${c.front} · ${c.back}`).join('  |  ')
  return {
    kind: 'VOCAB',
    id: created[0]?.id ?? '',
    title,
    items: created,
    undo: async () => {
      await Promise.all(created.map((c) => deleteVocabEntry(c.id)))
    },
  }
}

/** Receipt photo capture: image → expense draft → saved ledger rows, same echo/undo. */
export async function captureReceipt(image: File): Promise<CaptureResult> {
  const draft = dataOrThrow(await draftReceiptCapture({ body: { image } })) as CaptureDraft
  const items = draft.expense?.items ?? []
  if (items.length === 0) {
    throw new Error('No expense items found on the receipt')
  }
  return saveExpense(items)
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

  if (draft.kind === 'EXPENSE' && draft.expense?.items?.length) {
    return saveExpense(draft.expense.items)
  }

  if (draft.kind === 'STUDY' && draft.study?.items?.length) {
    return saveStudy(draft.study.items)
  }

  if (draft.kind === 'VOCAB' && draft.vocab?.items?.length) {
    return saveVocab(draft.vocab.items)
  }

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
