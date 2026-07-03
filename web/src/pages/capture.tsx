import { Link } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { CheckSquare, FileText, Loader2, Sparkles, Undo2 } from 'lucide-react'
import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { capture, type CaptureResult } from '@/lib/capture-api'
import { useNotes } from '@/lib/notes-api'
import { useTodayTasks, useUpcomingTasks, type Task } from '@/lib/tasks-api'
import { cn } from '@/lib/utils'

interface PendingCapture {
  key: number
  text: string
}

// Intersection (not extends): CaptureResult is a discriminated union.
type DoneCapture = CaptureResult & { key: number; undone?: boolean }

/**
 * Capture — the single AI write-path as a PAGE (no dialog): a big composer on
 * top, and what the AI just filed right below it. Undo stays available in the
 * session feed instead of a 4-second toast; older items live in Today/Notes.
 */
export function CapturePage() {
  const [text, setText] = useState('')
  const [pending, setPending] = useState<PendingCapture[]>([])
  const [done, setDone] = useState<DoneCapture[]>([])
  const nextKey = useRef(1)
  const queryClient = useQueryClient()

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
    queryClient.invalidateQueries({ queryKey: ['notes'] })
  }

  function fire(raw: string) {
    const key = nextKey.current++
    setPending((p) => [{ key, text: raw }, ...p])
    capture(raw)
      .then((result) => {
        setPending((p) => p.filter((x) => x.key !== key))
        setDone((d) => [{ ...result, key }, ...d])
        invalidate()
      })
      .catch(() => {
        setPending((p) => p.filter((x) => x.key !== key))
        toast.error('Capture thất bại — thử lại.', {
          action: { label: 'Retry', onClick: () => fire(raw) },
        })
      })
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  function submit() {
    const raw = text.trim()
    if (!raw) return
    setText('')
    fire(raw)
  }

  function undo(item: DoneCapture) {
    item.undo().then(() => {
      setDone((d) => d.map((x) => (x.key === item.key ? { ...x, undone: true } : x)))
      invalidate()
    })
  }

  return (
    <div className="mx-auto w-full max-w-3xl overflow-auto px-8 py-8">
      <div className="flex items-baseline gap-3">
        <h1 className="text-3xl font-bold tracking-tight">Capture</h1>
        <span className="text-sm text-muted-foreground">
          Ném vào bất kỳ gì — AI tự xếp thành task hoặc note.
        </span>
      </div>

      <Card className="mt-5 gap-0 p-3 focus-within:ring-2 focus-within:ring-ring/50">
        <div className="flex items-start gap-2">
          <Sparkles className="mt-1.5 size-4 shrink-0 text-primary" />
          <Textarea
            data-capture-input
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Gõ hoặc paste… task hay note, Enter để capture (Shift+Enter xuống dòng)"
            className="min-h-20 resize-none border-0 p-1 shadow-none focus-visible:ring-0"
            autoFocus
          />
        </div>
        <div className="flex justify-end">
          <Button size="sm" onClick={submit} disabled={!text.trim()}>
            Capture ⏎
          </Button>
        </div>
      </Card>

      {(pending.length > 0 || done.length > 0) && (
        <section className="mt-8">
          <h2 className="mb-1 text-sm font-semibold text-muted-foreground">Captured</h2>
          <div className="divide-y">
            {pending.map((p) => (
              <div key={p.key} className="flex items-center gap-3 py-3">
                <Loader2 className="size-4 shrink-0 animate-spin text-muted-foreground" />
                <span className="truncate text-sm italic text-muted-foreground">
                  Đang phân loại… “{p.text}”
                </span>
              </div>
            ))}
            {done.map((item) => (
              <FeedRow key={item.key} item={item} onUndo={() => undo(item)} />
            ))}
          </div>
        </section>
      )}

      <RecentSection />
    </div>
  )
}

function FeedRow({ item, onUndo }: { item: DoneCapture; onUndo: () => void }) {
  return (
    <div className={cn('flex items-center gap-3 py-3', item.undone && 'opacity-50')}>
      <Badge variant={item.kind === 'TASK' ? 'default' : 'secondary'} className="shrink-0">
        {item.kind === 'TASK' ? <CheckSquare className="size-3" /> : <FileText className="size-3" />}
        {item.kind === 'TASK' ? 'Task' : 'Note'}
      </Badge>
      <span className={cn('min-w-0 flex-1 truncate text-sm', item.undone && 'line-through')}>
        {item.title}
      </span>
      <span className="shrink-0 text-xs text-muted-foreground">
        {item.kind === 'TASK' ? (item.dueDate ? `due ${item.dueDate}` : 'no due date') : `→ ${item.folderPath || 'Root'}`}
      </span>
      {!item.undone && (
        <span className="flex shrink-0 items-center gap-1">
          {item.kind === 'NOTE' && (
            <Button asChild size="sm" variant="ghost">
              <Link to="/notes/$slug" params={{ slug: item.slug }}>
                Open
              </Link>
            </Button>
          )}
          <Button size="sm" variant="ghost" onClick={onUndo}>
            <Undo2 className="size-3.5" /> Undo
          </Button>
        </span>
      )}
      {item.undone && <span className="text-xs text-muted-foreground">đã hoàn tác</span>}
    </div>
  )
}

/** Recently created items across the app — capture history beyond this session. */
function RecentSection() {
  const { data: notes = [] } = useNotes('')
  const { data: today = [] } = useTodayTasks()
  const { data: upcoming = [] } = useUpcomingTasks(30)

  type Row = { kind: 'TASK' | 'NOTE'; key: string; title: string; info: string; slug?: string; createdAt: string }
  const taskRows: Row[] = [...today, ...upcoming].map((t: Task) => ({
    kind: 'TASK',
    key: `t-${t.id}`,
    title: t.title,
    info: t.dueDate ? `due ${t.dueDate}` : 'someday',
    createdAt: t.createdAt,
  }))
  const noteRows: Row[] = notes.map((n) => ({
    kind: 'NOTE',
    key: `n-${n.id}`,
    title: n.title,
    info: `→ ${n.folderPath || 'Root'}`,
    slug: n.slug,
    createdAt: n.createdAt,
  }))
  const rows = [...taskRows, ...noteRows]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 8)

  if (rows.length === 0) return null
  return (
    <section className="mt-8">
      <h2 className="mb-1 text-sm font-semibold text-muted-foreground">Recent</h2>
      <div className="divide-y">
        {rows.map((r) => (
          <div key={r.key} className="flex items-center gap-3 py-2.5 opacity-80">
            <Badge variant={r.kind === 'TASK' ? 'default' : 'secondary'} className="shrink-0">
              {r.kind === 'TASK' ? 'Task' : 'Note'}
            </Badge>
            {r.kind === 'NOTE' && r.slug ? (
              <Link to="/notes/$slug" params={{ slug: r.slug }} className="min-w-0 flex-1 truncate text-sm hover:underline">
                {r.title}
              </Link>
            ) : (
              <span className="min-w-0 flex-1 truncate text-sm">{r.title}</span>
            )}
            <span className="shrink-0 text-xs text-muted-foreground">{r.info}</span>
          </div>
        ))}
      </div>
    </section>
  )
}
