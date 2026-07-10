import { Link, useNavigate, useParams } from '@tanstack/react-router'
import {
  ArrowLeft,
  CalendarDays,
  CheckSquare,
  FileText,
  FolderKanban,
  Pencil,
  Plus,
  Trash2,
} from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { m } from '@/components/motion-primitives'
import {
  Dialog,
  DialogContent,
  DialogDescription,
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
  useDeleteDiscipline,
  useDisciplineCards,
  useDisciplineOverview,
  useUpdateDiscipline,
  type Discipline,
  type DisciplineCard,
  type DisciplineInput,
} from '@/lib/disciplines-api'
import { DISCIPLINE_DOT as DOT, DISCIPLINE_COLORS } from '@/lib/discipline-colors'
import { cn } from '@/lib/utils'

const COLORS = DISCIPLINE_COLORS

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
  const [deleting, setDeleting] = useState<DisciplineCard | null>(null)
  const navigate = useNavigate()

  return (
    <div className="w-full flex-1 overflow-auto px-4 py-6 md:px-10 md:py-8">
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

      <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {cards.map((card, index) => (
          <DisciplineCardItem
            key={card.discipline.id}
            card={card}
            index={index}
            onOpen={() => navigate({ to: '/disciplines/$id', params: { id: card.discipline.id } })}
            onDelete={() => setDeleting(card)}
          />
        ))}
        {!isLoading && cards.length === 0 && (
          <p className="col-span-full py-10 text-center text-sm text-muted-foreground">
            No disciplines yet — create the areas you want to grow in.
          </p>
        )}
      </div>

      {creating && <DisciplineDialog open onClose={() => setCreating(false)} />}
      {deleting && (
        <DeleteDisciplineDialog
          card={deleting}
          onClose={() => setDeleting(null)}
        />
      )}
    </div>
  )
}

function DisciplineCardItem({
  card,
  index,
  onOpen,
  onDelete,
}: {
  card: DisciplineCard
  index: number
  onOpen: () => void
  onDelete: () => void
}) {
  const blocked = linkedWorkCount(card) > 0
  return (
    <m.article initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: Math.min(index, 8) * 0.05 }} className="rounded-lg border p-5 transition-colors hover:bg-accent/50">
      <div className="flex items-start gap-3">
        <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-left">
          <div className="flex items-center gap-2.5">
            <span className={cn('size-3 shrink-0 rounded-full', DOT[card.discipline.color])} />
            <span className="truncate text-base font-semibold">{card.discipline.name}</span>
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-muted-foreground">
            <span className="inline-flex items-center gap-1.5">
              <FolderKanban className="size-3.5" /> {card.projects ?? 0} projects
            </span>
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
        <Button
          type="button"
          size="icon-sm"
          variant="ghost"
          disabled={blocked}
          className="text-muted-foreground hover:text-destructive"
          aria-label={`Delete ${card.discipline.name}`}
          title={blocked ? deleteBlockedTitle(card) : `Delete ${card.discipline.name}`}
          onClick={onDelete}
        >
          <Trash2 className="size-4" />
        </Button>
      </div>
    </m.article>
  )
}

/** One discipline's slice: everything it currently holds, in one view. */
export function DisciplinePage() {
  const { id } = useParams({ from: '/disciplines/$id' })
  const { data: overview, isLoading } = useDisciplineOverview(id)
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const navigate = useNavigate()

  if (isLoading || !overview) {
    return <div className="w-full flex-1 px-4 py-6 md:px-10 md:py-8" />
  }
  const d = overview.discipline
  const noteCount = overview.noteCount ?? 0

  return (
    <div className="w-full flex-1 overflow-auto px-4 py-6 md:px-10 md:py-8">
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
        <Button
          size="icon"
          variant="ghost"
          className="size-8 text-muted-foreground hover:text-destructive"
          aria-label="Delete discipline"
          title="Delete discipline"
          onClick={() => setDeleting(true)}
        >
          <Trash2 className="size-4" />
        </Button>
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        {overview.openTasks.length} open tasks · {overview.upcomingEvents.length} events this week
        · {noteCount} notes
      </p>

      <div className="mt-8 grid grid-cols-1 gap-10 lg:grid-cols-2 xl:grid-cols-4">
        <SliceColumn title="Projects" emptyText="No projects yet — start one from the Projects page.">
          {(overview.projects ?? []).map((p) => (
            <Link key={p.id} to="/projects" className="group block py-2">
              <div className="flex items-baseline gap-3">
                <span
                  className={cn(
                    'min-w-0 flex-1 truncate text-sm group-hover:underline',
                    p.status === 'DONE' && 'text-muted-foreground line-through',
                  )}
                >
                  {p.name}
                </span>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {p.progressPercent ?? 0}%
                </span>
              </div>
              <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-primary"
                  style={{ width: `${p.progressPercent ?? 0}%` }}
                />
              </div>
            </Link>
          ))}
        </SliceColumn>

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
      {deleting && (
        <DeleteDisciplineDialog
          target={{ discipline: { id: d.id, name: d.name, color: d.color } }}
          onClose={() => setDeleting(false)}
          onDeleted={() => navigate({ to: '/disciplines' })}
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

function DeleteDisciplineDialog({
  card,
  target,
  onClose,
  onDeleted,
}: {
  card?: DisciplineCard
  target?: DisciplineDeleteTarget
  onClose: () => void
  onDeleted?: () => void
}) {
  const remove = useDeleteDiscipline()
  const discipline = card?.discipline ?? target?.discipline
  const blocked = card ? linkedWorkCount(card) > 0 : false

  if (!discipline) return null
  const selected = discipline

  function submit() {
    if (blocked) return
    remove
      .mutateAsync(selected.id)
      .then(() => {
        toast.success(`Deleted "${selected.name}"`)
        onDeleted?.()
        onClose()
      })
      .catch(() => toast.error('Delete failed — move linked projects, tasks or events first.'))
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Delete discipline?</DialogTitle>
          <DialogDescription>
            This permanently removes "{discipline.name}". Notes with matching tags are kept.
          </DialogDescription>
        </DialogHeader>
        <div className="rounded-md border bg-muted/30 p-3 text-sm text-muted-foreground">
          {card ? (
            blocked ? (
              <p>{deleteBlockedTitle(card)}</p>
            ) : (
              <p>No linked projects, tasks or events were found for this discipline.</p>
            )
          ) : (
            <p>
              If projects, tasks or events are still linked, the server will refuse this delete.
            </p>
          )}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={submit} disabled={blocked || remove.isPending}>
            <Trash2 className="size-4" /> Delete
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

type DisciplineDeleteTarget = {
  discipline: Discipline
}

function linkedWorkCount(card: DisciplineCard): number {
  return (card.projects ?? 0) + (card.linkedTasks ?? 0) + (card.linkedEvents ?? 0)
}

function deleteBlockedTitle(card: DisciplineCard): string {
  return `Move or delete linked work first: ${card.projects ?? 0} projects, ${card.linkedTasks ?? 0} tasks, ${card.linkedEvents ?? 0} events`
}
