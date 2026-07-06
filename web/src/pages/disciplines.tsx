import { Link, useNavigate, useParams } from '@tanstack/react-router'
import {
  ArrowLeft,
  CalendarDays,
  CheckSquare,
  FileText,
  Pencil,
  Plus,
} from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  useCreateDiscipline,
  useDisciplineCards,
  useDisciplineOverview,
  useUpdateDiscipline,
  type Discipline,
  type DisciplineInput,
} from '@/lib/disciplines-api'
import { cn } from '@/lib/utils'

const DOT: Record<Discipline['color'], string> = {
  BLUE: 'bg-blue-600',
  GREEN: 'bg-green-600',
  RED: 'bg-red-600',
  YELLOW: 'bg-yellow-600',
  PURPLE: 'bg-purple-600',
  ORANGE: 'bg-orange-600',
  GRAY: 'bg-neutral-600',
}

const COLORS = Object.keys(DOT) as Discipline['color'][]

function formatDay(isoInstant: string): string {
  const d = new Date(isoInstant)
  return d.toLocaleDateString(undefined, { weekday: 'short', day: '2-digit', month: '2-digit' })
}

function formatTime(isoInstant: string): string {
  const d = new Date(isoInstant)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** Grid of every discipline with live counts — the LDP spine made visible. */
export function DisciplinesPage() {
  const { data: cards = [], isLoading } = useDisciplineCards()
  const [creating, setCreating] = useState(false)
  const navigate = useNavigate()

  return (
    <div className="w-full flex-1 overflow-auto px-10 py-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Disciplines</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            The areas you train — tasks, events and notes all hang off one.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus className="size-4" /> New discipline
        </Button>
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {cards.map((card) => (
          <button
            key={card.discipline.id}
            type="button"
            onClick={() =>
              navigate({ to: '/disciplines/$id', params: { id: card.discipline.id } })
            }
            className="rounded-xl border p-5 text-left transition-colors hover:bg-accent/50"
          >
            <div className="flex items-center gap-2.5">
              <span className={cn('size-3 shrink-0 rounded-full', DOT[card.discipline.color])} />
              <span className="truncate text-base font-semibold">{card.discipline.name}</span>
            </div>
            <div className="mt-4 flex items-center gap-4 text-sm text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                <CheckSquare className="size-3.5" /> {card.openTasks} open
              </span>
              <span className="inline-flex items-center gap-1.5">
                <CalendarDays className="size-3.5" /> {card.upcomingEvents} this week
              </span>
              <span className="inline-flex items-center gap-1.5">
                <FileText className="size-3.5" /> {card.notes} notes
              </span>
            </div>
          </button>
        ))}
        {!isLoading && cards.length === 0 && (
          <p className="col-span-full py-10 text-center text-sm text-muted-foreground">
            No disciplines yet — create the areas you want to grow in.
          </p>
        )}
      </div>

      {creating && <DisciplineDialog open onClose={() => setCreating(false)} />}
    </div>
  )
}

/** One discipline's slice: everything it currently holds, in one view. */
export function DisciplinePage() {
  const { id } = useParams({ from: '/disciplines/$id' })
  const { data: overview, isLoading } = useDisciplineOverview(id)
  const [editing, setEditing] = useState(false)

  if (isLoading || !overview) {
    return <div className="w-full flex-1 px-10 py-8" />
  }
  const d = overview.discipline
  const noteCount = overview.noteCount ?? 0

  return (
    <div className="w-full flex-1 overflow-auto px-10 py-8">
      <Button asChild size="sm" variant="ghost" className="-ml-2 mb-3 text-muted-foreground">
        <Link to="/disciplines">
          <ArrowLeft className="size-4" /> Disciplines
        </Link>
      </Button>
      <div className="flex items-center gap-3">
        <span className={cn('size-3.5 shrink-0 rounded-full', DOT[d.color])} />
        <h1 className="text-3xl font-bold tracking-tight">{d.name}</h1>
        <Button
          size="icon"
          variant="ghost"
          className="size-8 text-muted-foreground"
          aria-label="Edit discipline"
          onClick={() => setEditing(true)}
        >
          <Pencil className="size-4" />
        </Button>
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        {overview.openTasks.length} open tasks · {overview.upcomingEvents.length} events this week
        · {noteCount} notes
      </p>

      <div className="mt-8 grid gap-10 lg:grid-cols-3">
        <SliceColumn title="Open tasks" emptyText="Nothing open — capture or plan something.">
          {overview.openTasks.map((t) => (
            <Link key={t.id} to="/tasks" className="group flex items-baseline gap-3 py-2">
              <span className="min-w-0 flex-1 truncate text-sm group-hover:underline">
                {t.title}
              </span>
              <span className="shrink-0 text-xs text-muted-foreground">
                {t.dueDate
                  ? `due ${formatDay(t.dueDate + 'T00:00:00')}${t.dueTime ? ` ${t.dueTime.slice(0, 5)}` : ''}`
                  : 'someday'}
              </span>
            </Link>
          ))}
        </SliceColumn>

        <SliceColumn title="Next 7 days" emptyText="No events this week.">
          {overview.upcomingEvents.map((e) => (
            <Link
              key={`${e.id}@${e.startAt}`}
              to="/calendar"
              className="group flex items-baseline gap-3 py-2"
            >
              <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                {formatDay(e.startAt)}
                {!e.allDay && ` ${formatTime(e.startAt)}`}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm group-hover:underline">
                {e.title}
              </span>
            </Link>
          ))}
        </SliceColumn>

        <SliceColumn
          title={`Notes${noteCount > overview.recentNotes.length ? ` · ${noteCount}` : ''}`}
          emptyText="No notes tagged with this discipline yet."
        >
          {overview.recentNotes.map((n) => (
            <Link
              key={n.id}
              to="/notes/$slug"
              params={{ slug: n.slug }}
              className="group flex items-baseline gap-3 py-2"
            >
              <span className="min-w-0 flex-1 truncate text-sm group-hover:underline">
                {n.title}
              </span>
              <span className="shrink-0 text-xs text-muted-foreground">
                {n.folderPath || 'Root'}
              </span>
            </Link>
          ))}
        </SliceColumn>
      </div>

      {editing && (
        <DisciplineDialog
          open
          onClose={() => setEditing(false)}
          existing={{ id: d.id, name: d.name, color: d.color }}
        />
      )}
    </div>
  )
}

function SliceColumn({
  title,
  emptyText,
  children,
}: {
  title: string
  emptyText: string
  children: React.ReactNode
}) {
  const empty = !children || (Array.isArray(children) && children.length === 0)
  return (
    <section>
      <h2 className="mb-2 text-sm font-semibold text-muted-foreground">{title}</h2>
      <div className="divide-y">
        {empty ? <p className="py-3 text-sm italic text-muted-foreground">{emptyText}</p> : children}
      </div>
    </section>
  )
}

/** Create (no `existing`) or rename/recolor (with it) — one small form. */
function DisciplineDialog({
  open,
  onClose,
  existing,
}: {
  open: boolean
  onClose: () => void
  existing?: Discipline
}) {
  const [name, setName] = useState(existing?.name ?? '')
  const [color, setColor] = useState<Discipline['color']>(existing?.color ?? 'BLUE')
  const create = useCreateDiscipline()
  const update = useUpdateDiscipline()

  function submit() {
    const body: DisciplineInput = { name: name.trim(), color }
    if (!body.name) return
    const call = existing
      ? update.mutateAsync({ id: existing.id, ...body })
      : create.mutateAsync(body)
    call
      .then(() => {
        toast.success(existing ? 'Discipline updated' : `Discipline "${body.name}" created`)
        if (!existing) setName('')
        onClose()
      })
      .catch(() => toast.error('Saving failed — try again.'))
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>{existing ? 'Edit discipline' : 'New discipline'}</DialogTitle>
        </DialogHeader>
        <div className="grid gap-4">
          <div className="grid gap-2">
            <Label htmlFor="discipline-name">Name</Label>
            <Input
              id="discipline-name"
              value={name}
              onChange={(e) => setName(e.currentTarget.value)}
              placeholder="English · IELTS"
              onKeyDown={(e) => e.key === 'Enter' && submit()}
            />
          </div>
          <div className="grid gap-2">
            <Label>Color</Label>
            <Select value={color} onValueChange={(v) => setColor(v as Discipline['color'])}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {COLORS.map((c) => (
                  <SelectItem key={c} value={c}>
                    <span className="inline-flex items-center gap-2">
                      <span className={cn('size-2.5 rounded-full', DOT[c])} />
                      {c.charAt(0) + c.slice(1).toLowerCase()}
                    </span>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!name.trim()}>
            {existing ? 'Save' : 'Create'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
