import { Clock } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { KeyboardSensor, PointerSensor, useSensor, useSensors } from '@dnd-kit/core'
import { toast } from 'sonner'
import { Checkbox } from '@/components/ui/checkbox'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  KanbanBoard,
  KanbanCard,
  KanbanCards,
  KanbanHeader,
  KanbanProvider,
  type DragEndEvent,
} from '@/components/kibo-ui/kanban'
import { useDisciplines, type Discipline } from '@/lib/disciplines-api'
import { useRangeTasks, useSetTaskDone, useSomedayTasks, useUpdateTask, type Task } from '@/lib/tasks-api'
import { cn } from '@/lib/utils'

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function addDays(isoDate: string, days: number): string {
  const d = new Date(isoDate + 'T00:00:00')
  d.setDate(d.getDate() + days)
  return iso(d)
}

const DOT: Record<Discipline['color'], string> = {
  BLUE: 'bg-blue-600',
  GREEN: 'bg-green-600',
  RED: 'bg-red-600',
  YELLOW: 'bg-yellow-600',
  PURPLE: 'bg-purple-600',
  ORANGE: 'bg-orange-600',
  GRAY: 'bg-neutral-600',
}

/**
 * The time buckets both projections share. Columns are TIME, not workflow —
 * task status is deliberately binary OPEN/DONE (friction rule), so "what do I
 * do next" is answered by when it's due, and dropping a card on the board
 * simply rewrites its due date.
 */
const BUCKETS = [
  { id: 'overdue', name: 'Overdue', tone: 'danger', dot: 'bg-red-600' },
  { id: 'today', name: 'Today', tone: undefined, dot: 'bg-orange-500' },
  { id: 'week', name: 'This week', tone: undefined, dot: 'bg-blue-600' },
  { id: 'later', name: 'Later', tone: undefined, dot: 'bg-sky-400' },
  { id: 'someday', name: 'Someday', tone: 'muted', dot: 'bg-neutral-400' },
  { id: 'done', name: 'Done', tone: 'muted', dot: 'bg-green-600' },
] as const

type Bucket = (typeof BUCKETS)[number]
type BucketId = Bucket['id']
type Group = { bucket: Bucket; tasks: Task[] }

/**
 * Tasks — one store, two projections (Todoist-style): List and Board group by
 * due date. The time view lives on the Calendar page, which overlays these
 * same tasks onto the real calendar. Toggling a checkbox works identically in
 * every view. The discipline chips filter both projections (LDP spine).
 */
export function TasksPage() {
  const today = iso(new Date())
  const [view, setView] = useState('list')
  const [disciplineFilter, setDisciplineFilter] = useState<string | null>(null)
  const { data: disciplines = [] } = useDisciplines()

  return (
    <div className="w-full overflow-auto px-8 py-8">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">Tasks</h1>
        <Tabs value={view} onValueChange={setView}>
          <TabsList>
            <TabsTrigger value="list">List</TabsTrigger>
            <TabsTrigger value="board">Board</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>
      {disciplines.length > 0 && (
        <div className="mt-4 flex flex-wrap items-center gap-1.5">
          <button
            type="button"
            onClick={() => setDisciplineFilter(null)}
            className={cn(
              'rounded-full border px-2.5 py-0.5 text-xs',
              disciplineFilter === null ? 'border-primary bg-primary/10 font-medium' : 'text-muted-foreground hover:bg-muted',
            )}
          >
            Tất cả
          </button>
          {disciplines.map((d) => (
            <button
              key={d.id}
              type="button"
              onClick={() => setDisciplineFilter(disciplineFilter === d.id ? null : d.id)}
              className={cn(
                'flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs',
                disciplineFilter === d.id ? 'border-primary bg-primary/10 font-medium' : 'text-muted-foreground hover:bg-muted',
              )}
            >
              <span className={cn('size-2 rounded-full', DOT[d.color])} />
              {d.name}
            </button>
          ))}
        </div>
      )}
      <GroupedViews today={today} board={view === 'board'} disciplines={disciplines} disciplineFilter={disciplineFilter} />
    </div>
  )
}

/** Shared bucketing for List and Board — same groups, different layout. */
function GroupedViews({
  today,
  board,
  disciplines,
  disciplineFilter,
}: {
  today: string
  board: boolean
  disciplines: Discipline[]
  disciplineFilter: string | null
}) {
  const { data: dated = [] } = useRangeTasks(addDays(today, -60), addDays(today, 90))
  const { data: someday = [] } = useSomedayTasks()

  const byDiscipline = (t: Task) => disciplineFilter === null || t.disciplineId === disciplineFilter
  const filteredDated = dated.filter(byDiscipline)
  const filteredSomeday = someday.filter(byDiscipline)

  const weekEnd = addDays(today, 7)
  const open = filteredDated.filter((t) => t.status === 'OPEN')
  const tasksOf = (id: BucketId): Task[] => {
    switch (id) {
      case 'overdue':
        return open.filter((t) => t.dueDate! < today)
      case 'today':
        return open.filter((t) => t.dueDate === today)
      case 'week':
        return open.filter((t) => t.dueDate! > today && t.dueDate! <= weekEnd)
      case 'later':
        return open.filter((t) => t.dueDate! > weekEnd)
      case 'someday':
        return filteredSomeday
      case 'done':
        return filteredDated
          .filter((t) => t.status === 'DONE')
          .sort((a, b) => (b.completedAt ?? '').localeCompare(a.completedAt ?? ''))
          .slice(0, 10)
    }
  }
  const groups: Group[] = BUCKETS.map((bucket) => ({ bucket, tasks: tasksOf(bucket.id) }))
  const visible = groups.filter((g) => g.tasks.length > 0)

  if (visible.length === 0) {
    return <p className="mt-8 text-sm text-muted-foreground">Chưa có task nào — Capture hoặc quick-add ở Today.</p>
  }

  if (board) {
    return <TaskKanban groups={groups} today={today} disciplines={disciplines} />
  }

  return (
    <div className="mx-auto mt-6 max-w-3xl space-y-6">
      {visible.map((g) => (
        <section key={g.bucket.id}>
          <GroupLabel label={g.bucket.name} count={g.tasks.length} tone={g.bucket.tone} />
          <div className="divide-y">
            {g.tasks.map((t) => (
              <TaskLine key={t.id} task={t} overdue={g.bucket.id === 'overdue'} disciplines={disciplines} />
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

type BoardItem = { id: string; name: string; column: string; task: Task }

/**
 * The Board projection as a real kanban (Kibo UI / dnd-kit): dropping a card
 * on a time column rewrites the task's DUE DATE (Today → hôm nay, This week →
 * ngày mai, Later → tuần sau, Someday → bỏ hạn); dropping on Done ticks it.
 * Overdue is machine-computed, so it accepts no drops.
 */
function TaskKanban({ groups, today, disciplines }: { groups: Group[]; today: string; disciplines: Discipline[] }) {
  const setDone = useSetTaskDone()
  const updateTask = useUpdateTask()

  const serverItems: BoardItem[] = useMemo(
    () => groups.flatMap((g) => g.tasks.map((t) => ({ id: t.id, name: t.title, column: g.bucket.id, task: t }))),
    [groups],
  )
  // Kibo's onDragOver mutates item.column in place, so the board works on
  // CLONES; origins live in a primitive map that mutation cannot touch.
  const originById = useMemo(() => new Map(serverItems.map((i) => [i.id, i.column])), [serverItems])
  const [items, setItems] = useState<BoardItem[]>(() => serverItems.map((i) => ({ ...i })))
  useEffect(() => setItems(serverItems.map((i) => ({ ...i }))), [serverItems])

  // Distance constraint keeps the checkbox clickable inside a draggable card.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor),
  )

  const commitMove = async (task: Task, target: BucketId) => {
    try {
      if (target === 'done') {
        await setDone.mutateAsync({ id: task.id, done: true })
        toast.success('Đã xong')
        return
      }
      if (task.status === 'DONE') {
        await setDone.mutateAsync({ id: task.id, done: false })
      }
      const dueDate =
        target === 'today' ? today : target === 'week' ? addDays(today, 1) : target === 'later' ? addDays(today, 8) : undefined
      await updateTask.mutateAsync({
        id: task.id,
        body: {
          title: task.title,
          notes: task.notes ?? undefined,
          dueDate,
          dueTime: dueDate ? (task.dueTime ?? undefined) : undefined,
          disciplineId: task.disciplineId ?? undefined,
        },
      })
      toast.success(dueDate ? `Dời hạn: ${new Date(dueDate + 'T00:00:00').toLocaleDateString('vi-VN')}` : 'Bỏ hạn — Someday')
    } catch {
      toast.error('Không lưu được — thử lại')
      setItems(serverItems.map((i) => ({ ...i })))
    }
  }

  const handleDragEnd = (event: DragEndEvent) => {
    const item = items.find((i) => i.id === event.active.id)
    if (!item) return
    const target = item.column as BucketId
    if (target === originById.get(item.id)) return
    if (target === 'overdue') {
      toast.warning('Overdue do hạn quá khứ — kéo vào Today để làm hôm nay')
      setItems(serverItems.map((i) => ({ ...i })))
      return
    }
    void commitMove(item.task, target)
  }

  return (
    <div className="mt-6 overflow-x-auto pb-4">
      <KanbanProvider
        columns={BUCKETS.map((b) => ({ ...b }))}
        data={items}
        onDataChange={setItems}
        onDragEnd={handleDragEnd}
        sensors={sensors}
        className="min-w-[1080px]"
      >
        {(column) => (
          <KanbanBoard id={column.id} key={column.id}>
            <KanbanHeader>
              <div className="flex items-center gap-2">
                <span className={cn('size-2 rounded-full', column.dot as string)} />
                <span className={cn(column.tone === 'danger' && 'text-destructive', column.tone === 'muted' && 'text-muted-foreground')}>
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
                  <BoardCard task={item.task} overdue={originById.get(item.id) === 'overdue'} disciplines={disciplines} />
                </KanbanCard>
              )}
            </KanbanCards>
          </KanbanBoard>
        )}
      </KanbanProvider>
    </div>
  )
}

/** Kanban card body: title wraps to two lines (VN titles die in one), meta row below. */
function BoardCard({ task, overdue, disciplines }: { task: Task; overdue?: boolean; disciplines: Discipline[] }) {
  const setDone = useSetTaskDone()
  const done = task.status === 'DONE'
  const discipline = task.disciplineId ? disciplines.find((d) => d.id === task.disciplineId) : undefined
  return (
    <div className="flex items-start gap-2.5">
      <Checkbox
        checked={done}
        onCheckedChange={() => setDone.mutate({ id: task.id, done: !done })}
        aria-label={task.title}
        className={cn('mt-0.5 rounded-full', overdue && 'border-destructive')}
      />
      <div className="min-w-0 flex-1">
        <p className={cn('line-clamp-2 text-sm leading-snug', done && 'text-muted-foreground line-through')}>{task.title}</p>
        {(discipline || task.dueDate) && (
          <div className="mt-1.5 flex items-center gap-2 text-xs text-muted-foreground">
            {discipline && (
              <span className="flex min-w-0 items-center gap-1">
                <span className={cn('size-2 shrink-0 rounded-full', DOT[discipline.color])} />
                <span className="truncate">{discipline.name}</span>
              </span>
            )}
            {task.dueDate && (
              <span className="ml-auto flex shrink-0 items-center gap-1">
                {task.dueTime && <Clock className="size-3" />}
                {new Date(task.dueDate + 'T00:00:00').toLocaleDateString('vi-VN', { day: 'numeric', month: 'numeric' })}
                {task.dueTime && ` · ${task.dueTime.slice(0, 5)}`}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function GroupLabel({ label, count, tone }: { label: string; count: number; tone?: 'danger' | 'muted' }) {
  return (
    <div className="flex items-center gap-2">
      <h2
        className={cn(
          'text-sm font-semibold',
          tone === 'danger' && 'text-destructive',
          tone === 'muted' && 'text-muted-foreground',
        )}
      >
        {label}
      </h2>
      <span className="rounded-full bg-muted px-1.5 text-xs text-muted-foreground">{count}</span>
    </div>
  )
}

function TaskLine({
  task,
  overdue,
  disciplines,
}: {
  task: Task
  overdue?: boolean
  disciplines: Discipline[]
}) {
  const setDone = useSetTaskDone()
  const done = task.status === 'DONE'
  const discipline = task.disciplineId ? disciplines.find((d) => d.id === task.disciplineId) : undefined
  return (
    <div className="flex items-center gap-3 py-2.5">
      <Checkbox
        checked={done}
        onCheckedChange={() => setDone.mutate({ id: task.id, done: !done })}
        aria-label={task.title}
        className={cn('rounded-full', overdue && 'border-destructive')}
      />
      <span className={cn('min-w-0 flex-1 truncate text-sm', done && 'text-muted-foreground line-through')}>
        {task.title}
      </span>
      {discipline && (
        <span
          title={discipline.name}
          className="flex shrink-0 items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground"
        >
          <span className={cn('size-2 rounded-full', DOT[discipline.color])} />
          {discipline.name}
        </span>
      )}
      {task.dueDate && (
        <span className="flex shrink-0 items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground">
          {task.dueTime && <Clock className="size-3" />}
          {new Date(task.dueDate + 'T00:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
          {task.dueTime && ` · ${task.dueTime.slice(0, 5)}`}
        </span>
      )}
    </div>
  )
}
