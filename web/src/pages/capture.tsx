import { Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  CheckSquare,
  ExternalLink,
  FileText,
  Loader2,
  Mic,
  Sparkles,
  Square,
  Tag,
  Trash2,
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
import { capture, deleteNote, transcribeAudio, type CaptureKind } from '@/lib/capture-api'
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
  const queryClient = useQueryClient()

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
    queryClient.invalidateQueries({ queryKey: ['notes'] })
  }

  function fire(raw: string, forced: CaptureKind | null) {
    const key = nextKey.current++
    setPending((p) => [{ key, text: raw }, ...p])
    capture(raw, forced ?? undefined)
      .then((result) => {
        setPending((p) => p.filter((x) => x.key !== key))
        invalidate()
        toast.success(
          `${result.kind === 'TASK' ? 'Task' : 'Note'}: ${result.title}`,
          {
            action: {
              label: 'Undo',
              onClick: () =>
                result.undo().then(invalidate).catch(() => toast.error('Undo thất bại.')),
            },
          },
        )
      })
      .catch(() => {
        setPending((p) => p.filter((x) => x.key !== key))
        toast.error('Capture thất bại — thử lại.', {
          action: { label: 'Retry', onClick: () => fire(raw, forced) },
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
    <div className="w-full flex-1 overflow-auto px-10 py-8">
      <div className="flex items-center gap-2">
        <h1 className="text-3xl font-bold tracking-tight">Capture</h1>
        <Sparkles className="size-5 text-primary" />
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        Nắm bắt ý tưởng ngay lập tức — AI tự xếp thành task hoặc note.
      </p>

      <PromptInput onSubmit={onSubmit} className="mt-5">
        <PromptInputBody>
          <PromptInputTextarea
            data-capture-input
            value={text}
            onChange={(e) => setText(e.currentTarget.value)}
            placeholder="Gõ hoặc paste… task hay note, Enter để capture"
            autoFocus
          />
        </PromptInputBody>
        <PromptInputFooter>
          <PromptInputTools>
            <PromptInputButton
              variant={kind === 'TASK' ? 'default' : 'ghost'}
              onClick={() => setKind((k) => (k === 'TASK' ? null : 'TASK'))}
            >
              <CheckSquare className="size-4" /> Thêm task
            </PromptInputButton>
            <PromptInputButton
              variant={kind === 'NOTE' ? 'default' : 'ghost'}
              onClick={() => setKind((k) => (k === 'NOTE' ? null : 'NOTE'))}
            >
              <FileText className="size-4" /> Thêm note
            </PromptInputButton>
            <MicButton onText={(t) => setText((prev) => (prev.trim() ? `${prev.trimEnd()} ${t}` : t))} />
          </PromptInputTools>
          <div className="flex items-center gap-3">
            <span className="hidden text-xs text-muted-foreground sm:block">
              Enter để capture · Shift+Enter xuống dòng
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

/**
 * Voice capture, memos-style dictation-first: record → server-side Whisper →
 * the text lands in the composer for REVIEW (the user still presses Capture),
 * and the recording is never stored.
 */
function MicButton({ onText }: { onText: (text: string) => void }) {
  const [state, setState] = useState<'idle' | 'recording' | 'transcribing'>('idle')
  const [seconds, setSeconds] = useState(0)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const timerRef = useRef<number | null>(null)

  async function start() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const recorder = MediaRecorder.isTypeSupported('audio/webm')
        ? new MediaRecorder(stream, { mimeType: 'audio/webm' })
        : new MediaRecorder(stream)
      chunksRef.current = []
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data)
      }
      recorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop())
        const blob = new Blob(chunksRef.current, { type: recorder.mimeType || 'audio/webm' })
        setState('transcribing')
        transcribeAudio(blob)
          .then((text) => {
            if (text.trim()) onText(text.trim())
            else toast.info('Không nghe được gì — thử nói gần mic hơn.')
          })
          .catch(() => toast.error('Transcribe thất bại — thử lại.'))
          .finally(() => setState('idle'))
      }
      recorderRef.current = recorder
      recorder.start()
      setSeconds(0)
      timerRef.current = window.setInterval(() => setSeconds((s) => s + 1), 1000)
      setState('recording')
    } catch {
      toast.error('Không truy cập được micro — kiểm tra quyền trình duyệt.')
    }
  }

  function stop() {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
    recorderRef.current?.stop()
  }

  if (state === 'transcribing') {
    return (
      <PromptInputButton variant="ghost" disabled>
        <Loader2 className="size-4 animate-spin" /> Đang chuyển chữ…
      </PromptInputButton>
    )
  }
  if (state === 'recording') {
    return (
      <PromptInputButton variant="destructive" onClick={stop}>
        <Square className="size-4" /> Dừng · {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}
      </PromptInputButton>
    )
  }
  return (
    <PromptInputButton variant="ghost" onClick={start} title="Nói để capture (không lưu file ghi âm)">
      <Mic className="size-4" /> Nói
    </PromptInputButton>
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
  createdAt: string
}

function formatDue(dueDate: string, dueTime: string | null | undefined): string {
  const [y, m, d] = dueDate.split('-')
  return `Hạn ${d}/${m}/${y}${dueTime ? ` · ${dueTime.slice(0, 5)}` : ''}`
}

/** One unified recent list — a finished capture surfaces at the top of it. */
function RecentSection({ pending }: { pending: PendingCapture[] }) {
  const { data: notes = [] } = useNotes('')
  const { data: today = [] } = useTodayTasks()
  const { data: upcoming = [] } = useUpcomingTasks(30)
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
  const all = [...taskRows, ...noteRows].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const rows = showAll ? all : all.slice(0, 8)

  function view(row: Row) {
    if (row.kind === 'NOTE' && row.slug) {
      navigate({ to: '/notes/$slug', params: { slug: row.slug } })
    } else {
      navigate({ to: '/tasks' })
    }
  }

  function remove(row: Row) {
    const del = row.kind === 'TASK' ? deleteTask(row.id) : deleteNote(row.id)
    del
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ['tasks'] })
        queryClient.invalidateQueries({ queryKey: ['notes'] })
        toast.success(`Đã xoá “${row.title}”`)
      })
      .catch(() => toast.error('Xoá thất bại — thử lại.'))
  }

  if (all.length === 0 && pending.length === 0) return null
  return (
    <section className="mt-8">
      <div className="mb-1 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-muted-foreground">Gần đây</h2>
        {all.length > 8 && (
          <Button size="sm" variant="ghost" onClick={() => setShowAll((s) => !s)}>
            {showAll ? 'Thu gọn' : 'Xem tất cả'}
          </Button>
        )}
      </div>
      <div className="divide-y">
        {pending.map((p) => (
          <div key={p.key} className="flex items-center gap-3 py-3">
            <Loader2 className="size-4 shrink-0 animate-spin text-muted-foreground" />
            <span className="truncate text-sm italic text-muted-foreground">
              Đang phân loại… “{p.text}”
            </span>
          </div>
        ))}
        {rows.map((r) => (
          <div key={r.key} className="flex items-center gap-3 py-2">
            <Badge variant={r.kind === 'TASK' ? 'default' : 'secondary'} className="shrink-0">
              {r.kind === 'TASK' ? 'Task' : 'Note'}
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
              ) : (
                <>
                  <Tag className="size-3 shrink-0" />
                  <span>{r.folderPath || 'Root'}</span>
                </>
              )}
            </span>
            <span className="flex shrink-0 items-center gap-0.5">
              <Button size="icon" variant="ghost" className="size-7" aria-label="Xem" title="Xem" onClick={() => view(r)}>
                <ExternalLink className="size-3.5" />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                className="size-7 text-muted-foreground hover:text-destructive"
                aria-label="Xoá"
                title="Xoá"
                onClick={() => remove(r)}
              >
                <Trash2 className="size-3.5" />
              </Button>
            </span>
          </div>
        ))}
      </div>
    </section>
  )
}
