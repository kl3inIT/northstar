import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { CheckSquare, Link2, Plus, Trash2, X } from 'lucide-react'
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
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { api } from '@/lib/api'
import { useDisciplines, type Discipline } from '@/lib/disciplines-api'
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
import { useQuery } from '@tanstack/react-query'
import { cn } from '@/lib/utils'

/** ColorName → a real CSS color for the Gantt status dot/bar. */
const HEX: Record<Discipline['color'], string> = {
  BLUE: '#2563eb',
  GREEN: '#16a34a',
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  PURPLE: '#9333ea',
  ORANGE: '#ea580c',
  GRAY: '#525252',
}

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** A bar needs a span: fall back to createdAt → +30 days until real dates are set. */
function featureOf(p: Project, disciplines: Discipline[]): GanttFeature {
  const discipline = disciplines.find((d) => d.id === p.disciplineId)
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
      color: p.status === 'DONE' ? '#a3a3a3' : HEX[discipline?.color ?? 'GRAY'],
    },
  }
}

export function ProjectsPage() {
  const { data: projects = [], isLoading } = useProjects()
  const { data: disciplines = [] } = useDisciplines()
  const update = useUpdateProject()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  // Group bars by discipline — the LDP spine is the Gantt's row grouping.
  const groups = useMemo(() => {
    const byDiscipline = new Map<string, { name: string; features: GanttFeature[] }>()
    for (const p of projects) {
      const d = disciplines.find((x) => x.id === p.disciplineId)
      const key = d?.name ?? 'No discipline'
      if (!byDiscipline.has(key)) byDiscipline.set(key, { name: key, features: [] })
      byDiscipline.get(key)!.features.push(featureOf(p, disciplines))
    }
    return [...byDiscipline.values()]
  }, [projects, disciplines])

  const selected = projects.find((p) => p.id === selectedId) ?? null

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
    <div className="flex w-full flex-1 flex-col overflow-hidden px-10 py-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Projects</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Staged work under a discipline — drag a bar to reschedule it.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>
          <Plus className="size-4" /> New project
        </Button>
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
          <GanttProvider range="monthly" zoom={100} className="h-full">
            <GanttSidebar>
              {groups.map((g) => (
                <GanttSidebarGroup key={g.name} name={g.name}>
                  {g.features.map((f) => (
                    <GanttSidebarItem key={f.id} feature={f} onSelectItem={setSelectedId} />
                  ))}
                </GanttSidebarGroup>
              ))}
            </GanttSidebar>
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
                          onClick={() => setSelectedId(f.id)}
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
      {selected && (
        <ProjectSheet project={selected} onClose={() => setSelectedId(null)} />
      )}
    </div>
  )
}

/** Side panel: milestones checklist, linked tasks, status — the project's cockpit. */
function ProjectSheet({ project, onClose }: { project: Project; onClose: () => void }) {
  const { data: disciplines = [] } = useDisciplines()
  const { data: tasks = [] } = useProjectTasks(project.id)
  const setDone = useSetProjectDone()
  const remove = useDeleteProject()
  const addMilestone = useAddMilestone()
  const toggleMilestone = useToggleMilestone()
  const removeMilestone = useRemoveMilestone()
  const setTaskProject = useSetTaskProject()
  const [editing, setEditing] = useState(false)
  const [milestoneName, setMilestoneName] = useState('')
  const discipline = disciplines.find((d) => d.id === project.disciplineId)
  const progress = project.progressPercent ?? 0

  function addStage() {
    const name = milestoneName.trim()
    if (!name) return
    addMilestone
      .mutateAsync({ projectId: project.id, name })
      .then(() => setMilestoneName(''))
      .catch(() => toast.error('Adding the milestone failed.'))
  }

  function deleteProject() {
    remove
      .mutateAsync(project.id)
      .then(() => {
        toast.success(`Deleted “${project.name}”`)
        onClose()
      })
      .catch(() => toast.error('Delete failed — try again.'))
  }

  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="flex w-full flex-col gap-0 overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle className="pr-8">{project.name}</SheetTitle>
          <SheetDescription>
            {discipline ? discipline.name : 'No discipline'}
            {project.startDate && ` · ${project.startDate}`}
            {project.targetDate && ` → ${project.targetDate}`}
          </SheetDescription>
        </SheetHeader>

        <div className="flex flex-col gap-6 px-4 pb-6">
          <div>
            <div className="mb-1.5 flex items-center justify-between text-sm">
              <span className="font-medium">{progress}% done</span>
              <label className="flex items-center gap-2 text-xs text-muted-foreground">
                Completed
                <Switch
                  checked={project.status === 'DONE'}
                  onCheckedChange={(done) =>
                    setDone.mutate({ id: project.id, done })
                  }
                />
              </label>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-muted">
              <div
                className="h-full rounded-full bg-primary transition-all"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>

          {project.notes && (
            <p className="whitespace-pre-wrap text-sm text-muted-foreground">{project.notes}</p>
          )}

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
                    onClick={() =>
                      removeMilestone.mutate({ projectId: project.id, milestoneId: m.id })
                    }
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

          <section>
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-muted-foreground">Tasks</h3>
              <LinkTaskPopover project={project} linkedIds={tasks.map((t) => t.id)} />
            </div>
            <div className="divide-y">
              {tasks.length === 0 && (
                <p className="py-2 text-sm italic text-muted-foreground">
                  No tasks attached yet.
                </p>
              )}
              {tasks.map((t) => (
                <div key={t.id} className="group flex items-center gap-2.5 py-2">
                  <CheckSquare
                    className={cn(
                      'size-3.5 shrink-0',
                      t.status === 'DONE' ? 'text-green-600' : 'text-muted-foreground',
                    )}
                  />
                  <span
                    className={cn(
                      'min-w-0 flex-1 truncate text-sm',
                      t.status === 'DONE' && 'text-muted-foreground line-through',
                    )}
                  >
                    {t.title}
                  </span>
                  {t.dueDate && (
                    <span className="shrink-0 text-xs text-muted-foreground">{t.dueDate}</span>
                  )}
                  <Button
                    size="icon"
                    variant="ghost"
                    className="size-6 opacity-0 group-hover:opacity-100"
                    aria-label="Detach task"
                    title="Detach from project"
                    onClick={() => setTaskProject.mutate({ taskId: t.id, projectId: null })}
                  >
                    <X className="size-3.5" />
                  </Button>
                </div>
              ))}
            </div>
          </section>

          <div className="mt-auto flex items-center justify-between border-t pt-4">
            <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
              Edit project
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="text-muted-foreground hover:text-destructive"
              onClick={deleteProject}
            >
              <Trash2 className="size-4" /> Delete
            </Button>
          </div>
        </div>

        {editing && (
          <ProjectDialog open onClose={() => setEditing(false)} existing={project} />
        )}
      </SheetContent>
    </Sheet>
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
      const { data, error } = await api.GET('/api/tasks/open', {
        params: { query: { disciplineId: project.disciplineId as string } },
      })
      if (error) throw error
      return data ?? []
    },
  })
  const linkable = candidates.filter((t) => !t.projectId && !linkedIds.includes(t.id))

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button size="sm" variant="ghost" className="h-7 text-xs">
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
