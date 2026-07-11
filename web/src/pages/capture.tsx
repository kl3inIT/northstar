import { Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import {
  BookOpenCheck,
  CalendarDays,
  Camera,
  CheckSquare,
  ExternalLink,
  FileText,
  GraduationCap,
  Loader2,
  Sparkles,
  Tag,
  Trash2,
  Wallet,
} from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import {
  PromptInput,
  PromptInputBody,
  PromptInputButton,
  PromptInputFooter,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputTools,
  type PromptInputMessage,
} from '@/components/ai-elements/prompt-input'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { deleteEvent, useRangeEvents } from '@/lib/calendar-api'
import { capture, captureReceipt, deleteLedgerEntry, deleteNote, deleteStudyEntry, deleteVocabEntry, type CaptureKind } from '@/lib/capture-api'
import { MicButton } from '@/components/mic-button'
import { m } from '@/components/motion-primitives'
import { useTransactions } from '@/lib/finance-api'
import { parseVocabMetadata, useStudySessions, useVocabCards } from '@/lib/study-api'
import { useNotes } from '@/lib/notes-api'
import { deleteTask, useTodayTasks, useUpcomingTasks, type Task } from '@/lib/tasks-api'
import { cn } from '@/lib/utils'

interface PendingCapture {
  key: number
  text: string
}

/**
 * Capture — the single AI write-path: an AI-Elements composer on top (with
 * chips to force task/note and to stamp a due phrase), and ONE unified
 * "Gần đây" list below where a finished capture simply surfaces at the top.
 * Undo lives in the success toast and in each row's ⋯ menu (Xoá).
 */
export function CapturePage() {
  const [text, setText] = useState('')
  const [kind, setKind] = useState<CaptureKind | null>(null)
  const [pending, setPending] = useState<PendingCapture[]>([])
  const nextKey = useRef(1)
  const receiptInput = useRef<HTMLInputElement>(null)
  const queryClient = useQueryClient()

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
    queryClient.invalidateQueries({ queryKey: ['notes'] })
    queryClient.invalidateQueries({ queryKey: ['calendar-events'] })
    queryClient.invalidateQueries({ queryKey: ['finance'] })
    queryClient.invalidateQueries({ queryKey: ['finance-summary'] })
    queryClient.invalidateQueries({ queryKey: ['study'] })
    queryClient.invalidateQueries({ queryKey: ['study-summary'] })
  }

  function report(result: Awaited<ReturnType<typeof capture>>) {
    invalidate()
    const label = result.kind === 'TASK' ? 'Task'
      : result.kind === 'EVENT' ? 'Event'
      : result.kind === 'EXPENSE' ? 'Expense'
      : result.kind === 'STUDY' ? 'Study'
      : result.kind === 'VOCAB' ? 'Vocab'
      : 'Note'
    toast.success(
      `${label}: ${result.title}`,
      {
        action: {
          label: 'Undo',
          onClick: () =>
            result.undo().then(invalidate).catch(() => toast.error('Undo failed.')),
        },
      },
    )
  }

  function fire(raw: string, forced: CaptureKind | null) {
    const key = nextKey.current++
    setPending((p) => [{ key, text: raw }, ...p])
    capture(raw, forced ?? undefined)
      .then((result) => {
        setPending((p) => p.filter((x) => x.key !== key))
        report(result)
      })
      .catch(() => {
        setPending((p) => p.filter((x) => x.key !== key))
        toast.error('Capture failed — try again.', {
          action: { label: 'Retry', onClick: () => fire(raw, forced) },
        })
      })
  }

  function fireReceipt(image: File) {
    const key = nextKey.current++
    setPending((p) => [{ key, text: `Reading receipt ${image.name}…` }, ...p])
    captureReceipt(image)
      .then((result) => {
        setPending((p) => p.filter((x) => x.key !== key))
        report(result)
      })
      .catch((e: Error) => {
        setPending((p) => p.filter((x) => x.key !== key))
        toast.error(e.message || 'Receipt capture failed — try again.', {
          action: { label: 'Retry', onClick: () => fireReceipt(image) },
        })
      })
  }

  function onSubmit(message: PromptInputMessage) {
    const raw = message.text.trim()
    if (!raw) return
    setText('')
    fire(raw, kind)
  }

  return (
    <div className="w-full flex-1 overflow-auto px-4 py-6 md:px-10 md:py-8">
      <div className="flex items-center gap-2">
        <h1 className="text-3xl font-bold tracking-tight">Capture</h1>
        <Sparkles className="size-5 text-primary" />
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        Capture anything instantly — AI files it in the right place.
      </p>

      <PromptInput onSubmit={onSubmit} className="mt-5">
        <PromptInputBody>
          <PromptInputTextarea
            data-capture-input
            value={text}
            onChange={(e) => setText(e.currentTarget.value)}
            placeholder="Type or paste… a task, note, event, expense, or study session"
            autoFocus
          />
        </PromptInputBody>
        <PromptInputFooter className="flex-wrap">
          <PromptInputTools className="flex-wrap">
            <PromptInputButton
              variant={kind === 'TASK' ? 'default' : 'ghost'}
              onClick={() => setKind((k) => (k === 'TASK' ? null : 'TASK'))}
            >
              <CheckSquare className="size-4" /> Add task
            </PromptInputButton>
            <PromptInputButton
              variant={kind === 'EVENT' ? 'default' : 'ghost'}
              onClick={() => setKind((k) => (k === 'EVENT' ? null : 'EVENT'))}
            >
              <CalendarDays className="size-4" /> Add event
            </PromptInputButton>
            <PromptInputButton
              variant={kind === 'NOTE' ? 'default' : 'ghost'}
              onClick={() => setKind((k) => (k === 'NOTE' ? null : 'NOTE'))}
            >
              <FileText className="size-4" /> Add note
            </PromptInputButton>
            <PromptInputButton
              variant={kind === 'EXPENSE' ? 'default' : 'ghost'}
              onClick={() => setKind((k) => (k === 'EXPENSE' ? null : 'EXPENSE'))}
            >
              <Wallet className="size-4" /> Add expense
            </PromptInputButton>
            <PromptInputButton
              variant={kind === 'STUDY' ? 'default' : 'ghost'}
              onClick={() => setKind((k) => (k === 'STUDY' ? null : 'STUDY'))}
            >
              <GraduationCap className="size-4" /> Log study
            </PromptInputButton>
            <PromptInputButton
              variant={kind === 'VOCAB' ? 'default' : 'ghost'}
              onClick={() => setKind((k) => (k === 'VOCAB' ? null : 'VOCAB'))}
            >
              <BookOpenCheck className="size-4" /> Save vocab
            </PromptInputButton>
            <PromptInputButton onClick={() => receiptInput.current?.click()}>
              <Camera className="size-4" /> Receipt
            </PromptInputButton>
            <input
              ref={receiptInput}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => {
                const file = e.currentTarget.files?.[0]
                e.currentTarget.value = ''
                if (file) fireReceipt(file)
              }}
            />
            <MicButton value={text} onChange={setText} />
          </PromptInputTools>
          <div className="flex items-center gap-3">
            <span className="hidden text-xs text-muted-foreground sm:block">
              Enter to capture · Shift+Enter for a new line
            </span>
            <PromptInputSubmit size="sm" disabled={!text.trim()}>
              Capture <Sparkles className="size-3.5" />
            </PromptInputSubmit>
          </div>
        </PromptInputFooter>
      </PromptInput>

      <RecentSection pending={pending} />
    </div>
  )
}

type Row = {
  kind: CaptureKind
  key: string
  id: string
  title: string
  slug?: string
  dueDate?: string | null
  dueTime?: string | null
  folderPath?: string
  startDate?: string
  allDay?: boolean
  amount?: number
  txType?: 'EXPENSE' | 'INCOME'
  category?: string
  studyMeta?: string
  createdAt: string
}

const VND_FMT = new Intl.NumberFormat('vi-VN')

function currentMonthKey(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function formatDue(dueDate: string, dueTime: string | null | undefined): string {
  const [y, m, d] = dueDate.split('-')
  return `Due ${d}/${m}/${y}${dueTime ? ` · ${dueTime.slice(0, 5)}` : ''}`
}

function formatEventStart(startDate: string, allDay: boolean | undefined): string {
  const d = new Date(startDate)
  const date = `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`
  if (allDay) return `${date} · all day`
  return `${date} · ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** Today → +30 days, anchored to the date so the react-query key is stable. */
function eventWindow(): { from: string; to: string } {
  const today = new Date()
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const to = new Date(from.getTime() + 30 * 24 * 60 * 60 * 1000)
  return { from: from.toISOString(), to: to.toISOString() }
}

/** Last 30 days as yyyy-MM-dd, anchored to the date for a stable query key. */
function studyWindow(): { from: string; to: string } {
  const today = new Date()
  const iso = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return { from: iso(new Date(today.getFullYear(), today.getMonth(), today.getDate() - 29)), to: iso(today) }
}

/** One unified recent list — a finished capture surfaces at the top of it. */
function RecentSection({ pending }: { pending: PendingCapture[] }) {
  const { data: notes = [] } = useNotes('')
  const { data: today = [] } = useTodayTasks()
  const { data: upcoming = [] } = useUpcomingTasks(30)
  const range = eventWindow()
  const { data: events = [] } = useRangeEvents(range.from, range.to)
  const { data: transactions = [] } = useTransactions(currentMonthKey())
  const studyRange = studyWindow()
  const { data: studySessions = [] } = useStudySessions(studyRange.from, studyRange.to)
  const { data: vocabCards = [] } = useVocabCards()
  const [showAll, setShowAll] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const taskRows: Row[] = [...today, ...upcoming].map((t: Task) => ({
    kind: 'TASK',
    key: `t-${t.id}`,
    id: t.id,
    title: t.title,
    dueDate: t.dueDate,
    dueTime: t.dueTime,
    createdAt: t.createdAt,
  }))
  const noteRows: Row[] = notes.map((n) => ({
    kind: 'NOTE',
    key: `n-${n.id}`,
    id: n.id,
    title: n.title,
    slug: n.slug,
    folderPath: n.folderPath,
    createdAt: n.createdAt,
  }))
  // One row per event ROW (a recurring series expands into many occurrences —
  // the capture list shows the series once, at its first upcoming occurrence).
  const seenEventIds = new Set<string>()
  const eventRows: Row[] = []
  for (const e of events) {
    const serverId = e.masterId ?? e.id
    if (e.kind !== 'event' || !e.createdAt || seenEventIds.has(serverId)) continue
    seenEventIds.add(serverId)
    eventRows.push({
      kind: 'EVENT',
      key: `e-${serverId}`,
      id: serverId,
      title: e.title,
      startDate: e.startDate,
      allDay: e.allDay,
      createdAt: e.createdAt,
    })
  }
  const expenseRows: Row[] = transactions.map((t) => ({
    kind: 'EXPENSE',
    key: `x-${t.id}`,
    id: t.id,
    title: t.description,
    amount: t.amount,
    txType: t.type,
    category: t.category,
    createdAt: t.createdAt,
  }))
  const studyRows: Row[] = studySessions.map((s) => ({
    kind: 'STUDY',
    key: `s-${s.id}`,
    id: s.id,
    title: s.notes ? `${s.skill} · ${s.notes}` : s.skill,
    studyMeta: [
      s.durationMinutes != null ? `${s.durationMinutes}m` : null,
      s.scoreRaw != null && s.scoreMax != null ? `${s.scoreRaw}/${s.scoreMax}` : null,
      s.kind === 'MOCK' ? 'mock' : null,
    ].filter(Boolean).join(' · '),
    createdAt: s.createdAt,
  }))
  const vocabRows: Row[] = vocabCards.map((c) => ({
    kind: 'VOCAB',
    key: `v-${c.id}`,
    id: c.id,
    title: `${c.front} · ${c.back}`,
    studyMeta: parseVocabMetadata(c.metadata).reading || 'card',
    createdAt: c.createdAt,
  }))
  const all = [...taskRows, ...noteRows, ...eventRows, ...expenseRows, ...studyRows, ...vocabRows].sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt),
  )
  const rows = showAll ? all : all.slice(0, 8)

  function view(row: Row) {
    if (row.kind === 'NOTE' && row.slug) {
      navigate({ to: '/notes/$slug', params: { slug: row.slug } })
    } else if (row.kind === 'EVENT') {
      navigate({ to: '/calendar' })
    } else if (row.kind === 'EXPENSE') {
      navigate({ to: '/finance' })
    } else if (row.kind === 'STUDY' || row.kind === 'VOCAB') {
      navigate({ to: '/study' })
    } else {
      navigate({ to: '/tasks' })
    }
  }

  function remove(row: Row) {
    const del =
      row.kind === 'TASK' ? deleteTask(row.id)
      : row.kind === 'EVENT' ? deleteEvent(row.id)
      : row.kind === 'EXPENSE' ? deleteLedgerEntry(row.id)
      : row.kind === 'STUDY' ? deleteStudyEntry(row.id)
      : row.kind === 'VOCAB' ? deleteVocabEntry(row.id)
      : deleteNote(row.id)
    del
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ['tasks'] })
        queryClient.invalidateQueries({ queryKey: ['notes'] })
        queryClient.invalidateQueries({ queryKey: ['calendar-events'] })
        queryClient.invalidateQueries({ queryKey: ['finance'] })
        queryClient.invalidateQueries({ queryKey: ['finance-summary'] })
        queryClient.invalidateQueries({ queryKey: ['study'] })
        queryClient.invalidateQueries({ queryKey: ['study-summary'] })
        queryClient.invalidateQueries({ queryKey: ['study-vocab'] })
        toast.success(`Deleted “${row.title}”`)
      })
      .catch(() => toast.error('Delete failed — try again.'))
  }

  if (all.length === 0 && pending.length === 0) return null
  return (
    <section className="mt-8">
      <div className="mb-1 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-muted-foreground">Recent</h2>
        {all.length > 8 && (
          <Button size="sm" variant="ghost" onClick={() => setShowAll((s) => !s)}>
            {showAll ? 'Show less' : 'Show all'}
          </Button>
        )}
      </div>
      <div className="divide-y">
        {pending.map((p) => (
          <m.div key={p.key} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} className="flex items-center gap-3 py-3">
            <Loader2 className="size-4 shrink-0 animate-spin text-muted-foreground" />
            <span className="truncate text-sm italic text-muted-foreground">
              Classifying… “{p.text}”
            </span>
          </m.div>
        ))}
        {rows.map((r, index) => (
          <m.div key={r.key} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(index, 8) * 0.035 }} className="flex items-center gap-3 py-2">
            <Badge
              variant={r.kind === 'TASK' ? 'default' : r.kind === 'NOTE' ? 'secondary' : 'outline'}
              className="shrink-0"
            >
              {r.kind === 'TASK' ? 'Task' : r.kind === 'EVENT' ? 'Event' : r.kind === 'EXPENSE' ? 'Expense' : r.kind === 'STUDY' ? 'Study' : r.kind === 'VOCAB' ? 'Vocab' : 'Note'}
            </Badge>
            {r.kind === 'NOTE' && r.slug ? (
              <Link
                to="/notes/$slug"
                params={{ slug: r.slug }}
                className="min-w-0 flex-1 truncate text-sm hover:underline"
              >
                {r.title}
              </Link>
            ) : (
              <span className="min-w-0 flex-1 truncate text-sm">{r.title}</span>
            )}
            <span
              className={cn(
                'inline-flex shrink-0 items-center gap-1 whitespace-nowrap text-xs leading-none text-muted-foreground',
                r.kind === 'TASK' && !r.dueDate && 'italic',
              )}
            >
              {r.kind === 'TASK' ? (
                <>
                  <CalendarDays className="size-3 shrink-0" />
                  <span>{r.dueDate ? formatDue(r.dueDate, r.dueTime) : 'someday'}</span>
                </>
              ) : r.kind === 'EVENT' ? (
                <>
                  <CalendarDays className="size-3 shrink-0" />
                  <span>{r.startDate ? formatEventStart(r.startDate, r.allDay) : ''}</span>
                </>
              ) : r.kind === 'EXPENSE' ? (
                <>
                  <Wallet className="size-3 shrink-0" />
                  <span className="tabular-nums">
                    {r.txType === 'INCOME' ? '+' : ''}{VND_FMT.format(r.amount ?? 0)} ₫ · {r.category}
                  </span>
                </>
              ) : r.kind === 'STUDY' ? (
                <>
                  <GraduationCap className="size-3 shrink-0" />
                  <span className="tabular-nums">{r.studyMeta || 'logged'}</span>
                </>
              ) : r.kind === 'VOCAB' ? (
                <>
                  <BookOpenCheck className="size-3 shrink-0" />
                  <span>{r.studyMeta}</span>
                </>
              ) : (
                <>
                  <Tag className="size-3 shrink-0" />
                  <span>{r.folderPath || 'Root'}</span>
                </>
              )}
            </span>
            <span className="flex shrink-0 items-center gap-0.5">
              <Button size="icon" variant="ghost" className="size-7" aria-label="View" title="View" onClick={() => view(r)}>
                <ExternalLink className="size-3.5" />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                className="size-7 text-muted-foreground hover:text-destructive"
                aria-label="Delete"
                title="Delete"
                onClick={() => remove(r)}
              >
                <Trash2 className="size-3.5" />
              </Button>
            </span>
          </m.div>
        ))}
      </div>
    </section>
  )
}
