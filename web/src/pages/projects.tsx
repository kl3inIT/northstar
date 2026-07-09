import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { toast } from 'sonner'
import { ArrowLeft, FileText, Link2, Plus, Star, Trash2, X } from 'lucide-react'
import { KeyboardSensor, PointerSensor, useSensor, useSensors } from '@dnd-kit/core'
import {
  GanttFeatureItem,
  GanttFeatureList,
  GanttFeatureListGroup,
  GanttHeader,
  GanttProvider,
  GanttSidebar,
  GanttSidebarGroup,
  GanttSidebarItem,
  GanttTimeline,
  GanttToday,
  type GanttFeature,
} from '@/components/kibo-ui/gantt'
import {
  KanbanBoard,
  KanbanCard,
  KanbanCards,
  KanbanHeader,
  KanbanProvider,
  type DragEndEvent,
} from '@/components/kibo-ui/kanban'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { useIsMobile } from '@/hooks/use-mobile'
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
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { listOpenTasksByDiscipline } from '@/lib/hey-api'
import type { TaskSummary } from '@/lib/hey-api'
import { dataOrThrow } from '@/lib/hey-api-result'
import { useDisciplines, type Discipline } from '@/lib/disciplines-api'
import { iso } from '@/lib/dates'
import {
  useCreateTask,
  useSetTaskDone,
  useSetTaskPlanned,
} from '@/lib/tasks-api'
import { useCreateNote, useProjectNotes } from '@/lib/notes-api'
import type { NoteSummary } from '@/lib/notes-types'
import {
  useAddMilestone,
  useCreateProject,
  useDeleteProject,
  useProjects,
  useProjectTasks,
  useRemoveMilestone,
  useSetProjectDone,
  useSetTaskProject,
  useToggleMilestone,
  useUpdateProject,
  type Project,
  type ProjectInput,
} from '@/lib/projects-api'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { cn } from '@/lib/utils'

type ProjTask = TaskSummary

const PROJECT_GANTT_COLORS = [
  '#2563eb',
  '#16a34a',
  '#dc2626',
  '#ca8a04',
  '#9333ea',
  '#ea580c',
  '#0891b2',
  '#be123c',
  '#4f46e5',
  '#0f766e',
] as const

function projectColor(id: string): string {
  let hash = 0
  for (const char of id) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  }
  return PROJECT_GANTT_COLORS[hash % PROJECT_GANTT_COLORS.length]
}

/** A bar needs a span: fall back to createdAt → +30 days until real dates are set. */
function featureOf(p: Project): GanttFeature {
  const start = p.startDate ? new Date(p.startDate + 'T00:00:00') : new Date(p.createdAt)
  const end = p.targetDate
    ? new Date(p.targetDate + 'T00:00:00')
    : new Date(start.getTime() + 30 * 86400000)
  return {
    id: p.id,
    name: p.name,
    startAt: start,
    endAt: end,
    status: {
      id: p.status,
      name: p.status,
      color: p.status === 'DONE' ? '#737373' : projectColor(p.id),
    },
  }
}

export function ProjectsPage() {
  const { data: projects = [], isLoading } = useProjects()
  const { data: disciplines = [] } = useDisciplines()
  const isMobile = useIsMobile()
  const navigate = useNavigate()
  const update = useUpdateProject()
  const [creating, setCreating] = useState(false)
  const [range, setRange] = useState<'monthly' | 'quarterly'>('monthly')

  const openProject = (id: string) => navigate({ to: '/projects/$id', params: { id } })

  // Group bars by discipline — the LDP spine is the Gantt's row grouping.
  const groups = useMemo(() => {
    const byDiscipline = new Map<string, { name: string; features: GanttFeature[] }>()
    for (const p of projects) {
      const d = disciplines.find((x) => x.id === p.disciplineId)
      const key = d?.name ?? 'No discipline'
      if (!byDiscipline.has(key)) byDiscipline.set(key, { name: key, features: [] })
      byDiscipline.get(key)!.features.push(featureOf(p))
    }
    return [...byDiscipline.values()]
  }, [projects, disciplines])

  function handleMove(id: string, startAt: Date, endAt: Date | null) {
    const p = projects.find((x) => x.id === id)
    if (!p) return
    update
      .mutateAsync({
        id,
        name: p.name,
        notes: p.notes ?? undefined,
        disciplineId: p.disciplineId ?? undefined,
        startDate: iso(startAt),
        targetDate: endAt ? iso(endAt) : undefined,
      })
      .catch(() => toast.error('Rescheduling failed — try again.'))
  }

  return (
    <div className="flex w-full flex-1 flex-col overflow-hidden px-4 py-6 md:px-10 md:py-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Projects</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Staged work under a discipline — drag a bar to reschedule, click to open its board.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={range}
            onValueChange={(v) => v && setRange(v as 'monthly' | 'quarterly')}
          >
            <ToggleGroupItem value="monthly">Month</ToggleGroupItem>
            <ToggleGroupItem value="quarterly">Quarter</ToggleGroupItem>
          </ToggleGroup>
          <Button onClick={() => setCreating(true)}>
            <Plus className="size-4" /> New project
          </Button>
        </div>
      </div>

      {projects.length === 0 && !isLoading ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
          <p className="text-sm text-muted-foreground">
            No projects yet — a scholarship application, an exam campaign…
          </p>
          <Button variant="outline" onClick={() => setCreating(true)}>
            <Plus className="size-4" /> Create the first one
          </Button>
        </div>
      ) : (
        <div className="mt-6 min-h-0 flex-1 overflow-hidden rounded-xl border">
          <GanttProvider range={range} zoom={100} className="h-full">
            {/* The 300px sidebar would leave a 390px phone a sliver of timeline —
                the provider measures the sidebar's presence in the DOM, so it must
                be unmounted (not display:none) on mobile. Names stay on the bars. */}
            {!isMobile && (
              <GanttSidebar>
                {groups.map((g) => (
                  <GanttSidebarGroup key={g.name} name={g.name}>
                    {g.features.map((f) => (
                      <GanttSidebarItem key={f.id} feature={f} onSelectItem={openProject} />
                    ))}
                  </GanttSidebarGroup>
                ))}
              </GanttSidebar>
            )}
            <GanttTimeline>
              <GanttHeader />
              <GanttFeatureList>
                {groups.map((g) => (
                  <GanttFeatureListGroup key={g.name}>
                    {g.features.map((f) => (
                      <GanttFeatureItem key={f.id} {...f} onMove={handleMove}>
                        <button
                          type="button"
                          className="flex-1 truncate text-left text-xs"
                          onClick={() => openProject(f.id)}
                        >
                          {f.name}
                        </button>
                      </GanttFeatureItem>
                    ))}
                  </GanttFeatureListGroup>
                ))}
              </GanttFeatureList>
              <GanttToday />
            </GanttTimeline>
          </GanttProvider>
        </div>
      )}

      {creating && <ProjectDialog open onClose={() => setCreating(false)} />}
    </div>
  )
}

/**
 * Full-page project cockpit (route /projects/$id): a header with progress, a
 * milestones rail, and — the main event — a task board. Opened from the Gantt.
 */
export function ProjectDetailPage() {
  const { id } = useParams({ from: '/projects/$id' })
  const { data: projects = [], isLoading } = useProjects()
  const { data: disciplines = [] } = useDisciplines()
  const project = projects.find((p) => p.id === id)

  if (isLoading) {
    return (
      <div className="flex w-full flex-1 items-center justify-center text-sm text-muted-foreground">
        Loading…
      </div>
    )
  }
  if (!project) {
    return (
      <div className="flex w-full flex-1 flex-col items-center justify-center gap-3 text-center">
        <p className="text-sm text-muted-foreground">This project no longer exists.</p>
        <Button asChild size="sm" variant="outline">
          <Link to="/projects">
            <ArrowLeft className="size-4" /> Back to projects
          </Link>
        </Button>
      </div>
    )
  }

  const discipline = disciplines.find((d) => d.id === project.disciplineId)
  return <ProjectDetail project={project} discipline={discipline} />
}

function ProjectDetail({ project, discipline }: { project: Project; discipline?: Discipline }) {
  const navigate = useNavigate()
  const setDone = useSetProjectDone()
  const remove = useDeleteProject()
  const [editing, setEditing] = useState(false)
  const progress = project.progressPercent ?? 0

  function deleteProject() {
    remove
      .mutateAsync(project.id)
      .then(() => {
        toast.success(`Deleted “${project.name}”`)
        navigate({ to: '/projects' })
      })
      .catch(() => toast.error('Delete failed — try again.'))
  }

  return (
    <div className="flex w-full flex-1 flex-col overflow-hidden px-4 py-6 md:px-10 md:py-8">
      <Link
        to="/projects"
        className="mb-3 inline-flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Projects
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-bold tracking-tight">{project.name}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {discipline ? discipline.name : 'No discipline'}
            {project.startDate && ` · ${project.startDate}`}
            {project.targetDate && ` → ${project.targetDate}`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            Completed
            <Switch
              checked={project.status === 'DONE'}
              onCheckedChange={(done) => setDone.mutate({ id: project.id, done })}
            />
          </label>
          <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
            Edit
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="text-muted-foreground hover:text-destructive"
            onClick={deleteProject}
          >
            <Trash2 className="size-4" />
          </Button>
        </div>
      </div>

      <div className="mt-3 flex items-center gap-3">
        <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
          <div
            className="h-full rounded-full bg-primary transition-all"
            style={{ width: `${progress}%` }}
          />
        </div>
        <span className="shrink-0 text-sm font-medium">{progress}%</span>
      </div>

      {project.notes && (
        <p className="project-notes-preview mt-3 max-w-4xl whitespace-pre-line text-sm text-muted-foreground">
          {project.notes}
        </p>
      )}

      {/* Board (main) + milestones rail. Rail drops below the board on mobile. */}
      <div className="mt-6 flex min-h-0 flex-1 flex-col gap-6 overflow-hidden lg:flex-row">
        <div className="flex min-h-0 flex-1 flex-col">
          <ProjectBoard project={project} />
        </div>
        <aside className="shrink-0 space-y-6 overflow-y-auto lg:w-72">
          <MilestonesPanel project={project} />
          <ProjectNotesPanel project={project} />
        </aside>
      </div>

      {editing && <ProjectDialog open onClose={() => setEditing(false)} existing={project} />}
    </div>
  )
}

const BOARD_COLUMNS = [
  { id: 'backlog', name: 'Backlog' },
  { id: 'planned', name: 'Planned' },
  { id: 'done', name: 'Done' },
] as const
type ColumnId = (typeof BOARD_COLUMNS)[number]['id']

/** Which column a task lands in: DONE → done, has a "do" day → planned, else backlog. */
function columnOf(t: ProjTask): ColumnId {
  if (t.status === 'DONE') return 'done'
  return t.plannedDate ? 'planned' : 'backlog'
}

type BoardItem = { id: string; name: string; column: string; task: ProjTask }

/**
 * The project's task board. Columns are NOT task status (tasks stay binary
 * OPEN/DONE) — they read the plannedDate "star": no star = Backlog, starred =
 * Planned, ticked = Done. Dropping a card just sets/clears that star (or ticks
 * it), so the board never invents a third status.
 */
function ProjectBoard({ project }: { project: Project }) {
  const { data: tasks = [] } = useProjectTasks(project.id)
  const setDone = useSetTaskDone()
  const setPlanned = useSetTaskPlanned()
  const createTask = useCreateTask()
  const queryClient = useQueryClient()
  const today = iso(new Date())
  const [newTitle, setNewTitle] = useState('')

  const serverItems: BoardItem[] = useMemo(
    () => tasks.map((t) => ({ id: t.id ?? '', name: t.title ?? '', column: columnOf(t), task: t })),
    [tasks],
  )
  // Kibo's onDragOver mutates item.column in place, so the board works on
  // CLONES; origins live in a primitive map that mutation cannot touch.
  const originById = useMemo(() => new Map(serverItems.map((i) => [i.id, i.column])), [serverItems])
  const [items, setItems] = useState<BoardItem[]>(() => serverItems.map((i) => ({ ...i })))
  useEffect(() => setItems(serverItems.map((i) => ({ ...i }))), [serverItems])

  // Distance constraint keeps the checkbox/star clickable inside a draggable card.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor),
  )

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['project-tasks', project.id] })
    queryClient.invalidateQueries({ queryKey: ['projects'] })
  }

  const commitMove = async (task: ProjTask, target: ColumnId) => {
    const id = task.id as string
    try {
      if (target === 'done') {
        await setDone.mutateAsync({ id, done: true })
      } else {
        if (task.status === 'DONE') await setDone.mutateAsync({ id, done: false })
        if (target === 'planned' && !task.plannedDate) {
          await setPlanned.mutateAsync({ id, plannedDate: today })
        } else if (target === 'backlog' && task.plannedDate) {
          await setPlanned.mutateAsync({ id, plannedDate: null })
        }
      }
      refresh()
    } catch {
      toast.error("Couldn't save — try again")
      setItems(serverItems.map((i) => ({ ...i })))
    }
  }

  const handleDragEnd = (event: DragEndEvent) => {
    const item = items.find((i) => i.id === event.active.id)
    if (!item) return
    const target = item.column as ColumnId
    if (target === originById.get(item.id)) return
    void commitMove(item.task, target)
  }

  async function addTask() {
    const title = newTitle.trim()
    if (!title) return
    setNewTitle('')
    try {
      await createTask.mutateAsync({
        title,
        disciplineId: project.disciplineId ?? undefined,
        projectId: project.id,
      })
      refresh()
    } catch {
      toast.error('Adding the task failed — try again.')
      setNewTitle(title)
    }
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="mb-3 flex items-center gap-2">
        <Input
          value={newTitle}
          onChange={(e) => setNewTitle(e.currentTarget.value)}
          placeholder="Add a task to this project…"
          className="h-8 max-w-sm"
          onKeyDown={(e) => e.key === 'Enter' && addTask()}
        />
        <Button size="sm" variant="outline" onClick={addTask} disabled={!newTitle.trim()}>
          <Plus className="size-4" /> New task
        </Button>
        <LinkTaskPopover project={project} linkedIds={items.map((i) => i.id)} />
      </div>

      {items.length === 0 ? (
        <p className="text-sm italic text-muted-foreground">
          No tasks yet — add one above, or link an existing open task.
        </p>
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto">
          <KanbanProvider
            columns={BOARD_COLUMNS.map((c) => ({ ...c }))}
            data={items}
            onDataChange={setItems}
            onDragEnd={handleDragEnd}
            sensors={sensors}
            className="grid-flow-row grid-cols-1 md:grid-cols-3"
          >
            {(column) => (
              <KanbanBoard id={column.id} key={column.id}>
                <KanbanHeader>
                  <div className="flex items-center gap-2">
                    <span
                      className={cn(
                        column.id === 'done' ? 'text-muted-foreground' : '',
                      )}
                    >
                      {column.name as string}
                    </span>
                    <span className="rounded-full bg-muted px-1.5 text-xs font-normal text-muted-foreground">
                      {items.filter((i) => i.column === column.id).length}
                    </span>
                  </div>
                </KanbanHeader>
                <KanbanCards id={column.id}>
                  {(item: BoardItem) => (
                    <KanbanCard key={item.id} {...item}>
                      <ProjectBoardCard task={item.task} today={today} projectId={project.id} />
                    </KanbanCard>
                  )}
                </KanbanCards>
              </KanbanBoard>
            )}
          </KanbanProvider>
        </div>
      )}
    </div>
  )
}

/** Board card: tick = OPEN/DONE, star = the Planned "do" day, X = detach from project. */
function ProjectBoardCard({
  task,
  today,
  projectId,
}: {
  task: ProjTask
  today: string
  projectId: string
}) {
  const setDone = useSetTaskDone()
  const setPlanned = useSetTaskPlanned()
  const setTaskProject = useSetTaskProject()
  const queryClient = useQueryClient()
  const id = task.id as string
  const done = task.status === 'DONE'
  const starred = !!task.plannedDate

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['project-tasks', projectId] })

  return (
    <div className="group flex items-start gap-2.5">
      <Checkbox
        checked={done}
        onCheckedChange={() =>
          setDone.mutate({ id, done: !done }, { onSuccess: refresh })
        }
        aria-label={task.title ?? ''}
        className="mt-0.5 rounded-full"
      />
      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-1.5">
          <p
            className={cn(
              'line-clamp-2 flex-1 text-sm leading-snug',
              done && 'text-muted-foreground line-through',
            )}
          >
            {task.title}
          </p>
          {!done && (
            <button
              type="button"
              aria-label={starred ? 'Remove from Planned' : 'Plan this'}
              title={starred ? 'Move to Backlog' : 'Plan it (set a do-day)'}
              onClick={(e) => {
                e.stopPropagation()
                setPlanned.mutate(
                  { id, plannedDate: starred ? null : today },
                  { onSuccess: refresh },
                )
              }}
              className="mt-0.5 shrink-0"
            >
              <Star
                className={cn(
                  'size-4',
                  starred
                    ? 'fill-amber-400 text-amber-400'
                    : 'text-muted-foreground/50 hover:text-amber-400',
                )}
              />
            </button>
          )}
          <button
            type="button"
            aria-label="Detach from project"
            title="Detach from project"
            onClick={(e) => {
              e.stopPropagation()
              setTaskProject.mutate({ taskId: id, projectId: null })
            }}
            className="mt-0.5 shrink-0 opacity-0 group-hover:opacity-100"
          >
            <X className="size-3.5 text-muted-foreground hover:text-destructive" />
          </button>
        </div>
        {task.dueDate && (
          <div className="mt-1.5 text-xs text-muted-foreground">
            {new Date(task.dueDate + 'T00:00:00').toLocaleDateString('vi-VN', {
              day: 'numeric',
              month: 'numeric',
            })}
          </div>
        )}
      </div>
    </div>
  )
}

function ProjectNotesPanel({ project }: { project: Project }) {
  const { data: notes = [], isLoading } = useProjectNotes(project.id)
  const createNote = useCreateNote()
  const navigate = useNavigate()

  function newNote() {
    const title = window.prompt('New project note title')?.trim()
    if (!title) return
    createNote.mutate(
      {
        title,
        folderPath: `Projects/${project.name}`,
        contentMarkdown: '',
        tags: [],
        projectId: project.id,
      },
      { onSuccess: (note) => navigate({ to: '/notes/$slug', params: { slug: note.slug } }) },
    )
  }

  return (
    <section>
      <div className="mb-2 flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-muted-foreground">Notes</h3>
        <Button
          size="icon"
          variant="ghost"
          className="size-7"
          aria-label="New project note"
          title="New project note"
          onClick={newNote}
          disabled={createNote.isPending}
        >
          <Plus className="size-3.5" />
        </Button>
      </div>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading notes...</p>
      ) : notes.length === 0 ? (
        <p className="text-sm italic text-muted-foreground">
          No linked notes yet.
        </p>
      ) : (
        <div className="space-y-2">
          {notes.map((note) => (
            <ProjectNoteCard key={note.id} note={note} />
          ))}
        </div>
      )}
    </section>
  )
}

function ProjectNoteCard({ note }: { note: NoteSummary }) {
  return (
    <Link
      to="/notes/$slug"
      params={{ slug: note.slug }}
      className="block rounded-md border p-3 transition-colors hover:bg-accent"
    >
      <div className="flex items-start gap-2">
        <FileText className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{note.title}</p>
          {note.snippet && (
            <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{note.snippet}</p>
          )}
        </div>
      </div>
    </Link>
  )
}

/** The milestones checklist rail — add a stage, tick it done, or remove it. */
function MilestonesPanel({ project }: { project: Project }) {
  const addMilestone = useAddMilestone()
  const toggleMilestone = useToggleMilestone()
  const removeMilestone = useRemoveMilestone()
  const [milestoneName, setMilestoneName] = useState('')

  function addStage() {
    const name = milestoneName.trim()
    if (!name) return
    addMilestone
      .mutateAsync({ projectId: project.id, name })
      .then(() => setMilestoneName(''))
      .catch(() => toast.error('Adding the milestone failed.'))
  }

  return (
    <section>
      <h3 className="mb-2 text-sm font-semibold text-muted-foreground">Milestones</h3>
      <div className="space-y-1">
        {(project.milestones ?? []).map((m) => (
          <div key={m.id} className="group flex items-center gap-2.5 py-1">
            <Checkbox
              checked={!!m.doneAt}
              onCheckedChange={() =>
                toggleMilestone.mutate({ projectId: project.id, milestoneId: m.id })
              }
            />
            <span
              className={cn(
                'min-w-0 flex-1 truncate text-sm',
                m.doneAt && 'text-muted-foreground line-through',
              )}
            >
              {m.name}
            </span>
            {m.dueDate && (
              <span className="shrink-0 text-xs text-muted-foreground">{m.dueDate}</span>
            )}
            <Button
              size="icon"
              variant="ghost"
              className="size-6 opacity-0 group-hover:opacity-100"
              aria-label="Remove milestone"
              onClick={() => removeMilestone.mutate({ projectId: project.id, milestoneId: m.id })}
            >
              <X className="size-3.5" />
            </Button>
          </div>
        ))}
      </div>
      <div className="mt-2 flex gap-2">
        <Input
          value={milestoneName}
          onChange={(e) => setMilestoneName(e.currentTarget.value)}
          placeholder="Add a stage…"
          className="h-8"
          onKeyDown={(e) => e.key === 'Enter' && addStage()}
        />
        <Button size="sm" variant="outline" onClick={addStage} disabled={!milestoneName.trim()}>
          Add
        </Button>
      </div>
    </section>
  )
}

/** Attach an open task of the same discipline that is not in a project yet. */
function LinkTaskPopover({ project, linkedIds }: { project: Project; linkedIds: string[] }) {
  const [open, setOpen] = useState(false)
  const setTaskProject = useSetTaskProject()
  const { data: candidates = [] } = useQuery({
    queryKey: ['open-tasks', project.disciplineId],
    enabled: open && !!project.disciplineId,
    queryFn: async () => {
      return dataOrThrow(await listOpenTasksByDiscipline({
        query: { disciplineId: project.disciplineId as string },
      }))
    },
  })
  const linkable = candidates.filter((t) => !t.projectId && !linkedIds.includes(t.id))

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button size="sm" variant="ghost" className="h-8 text-xs">
          <Link2 className="size-3.5" /> Link task
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-72 p-2">
        {!project.disciplineId ? (
          <p className="p-2 text-xs text-muted-foreground">
            Give the project a discipline first — tasks are picked from it.
          </p>
        ) : linkable.length === 0 ? (
          <p className="p-2 text-xs text-muted-foreground">
            No unattached open tasks in this discipline.
          </p>
        ) : (
          <div className="max-h-56 space-y-0.5 overflow-y-auto">
            {linkable.map((t) => (
              <button
                key={t.id}
                type="button"
                className="w-full truncate rounded-md px-2 py-1.5 text-left text-sm hover:bg-accent"
                onClick={() => {
                  setTaskProject.mutate({ taskId: t.id, projectId: project.id })
                  setOpen(false)
                }}
              >
                {t.title}
              </button>
            ))}
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}

/** Create (no `existing`) or edit the project's own fields. */
function ProjectDialog({
  open,
  onClose,
  existing,
}: {
  open: boolean
  onClose: () => void
  existing?: Project
}) {
  const { data: disciplines = [] } = useDisciplines()
  const create = useCreateProject()
  const update = useUpdateProject()
  const [name, setName] = useState(existing?.name ?? '')
  const [disciplineId, setDisciplineId] = useState(existing?.disciplineId ?? '')
  const [startDate, setStartDate] = useState(existing?.startDate ?? iso(new Date()))
  const [targetDate, setTargetDate] = useState(existing?.targetDate ?? '')
  const [notes, setNotes] = useState(existing?.notes ?? '')

  function submit() {
    const body: ProjectInput = {
      name: name.trim(),
      notes: notes.trim() || undefined,
      disciplineId: disciplineId || undefined,
      startDate: startDate || undefined,
      targetDate: targetDate || undefined,
    }
    if (!body.name) return
    const call = existing
      ? update.mutateAsync({ id: existing.id, ...body })
      : create.mutateAsync(body)
    call
      .then(() => {
        toast.success(existing ? 'Project updated' : `Project "${body.name}" created`)
        onClose()
      })
      .catch(() => toast.error('Saving failed — check the dates and try again.'))
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{existing ? 'Edit project' : 'New project'}</DialogTitle>
        </DialogHeader>
        <div className="grid gap-4">
          <div className="grid gap-2">
            <Label htmlFor="project-name">Name</Label>
            <Input
              id="project-name"
              value={name}
              onChange={(e) => setName(e.currentTarget.value)}
              placeholder="Chevening application 2027"
            />
          </div>
          <div className="grid gap-2">
            <Label>Discipline</Label>
            <Select value={disciplineId} onValueChange={setDisciplineId}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Pick a discipline" />
              </SelectTrigger>
              <SelectContent>
                {disciplines.map((d) => (
                  <SelectItem key={d.id} value={d.id}>
                    {d.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="project-start">Start</Label>
              <Input
                id="project-start"
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.currentTarget.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="project-target">Target</Label>
              <Input
                id="project-target"
                type="date"
                value={targetDate}
                onChange={(e) => setTargetDate(e.currentTarget.value)}
              />
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="project-notes">Notes</Label>
            <Textarea
              id="project-notes"
              value={notes}
              onChange={(e) => setNotes(e.currentTarget.value)}
              rows={3}
            />
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
