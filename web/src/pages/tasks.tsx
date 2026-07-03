import { ChevronLeft, ChevronRight, Clock } from 'lucide-react'
import { useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useRangeTasks, useSetTaskDone, useSomedayTasks, type Task } from '@/lib/tasks-api'
import { cn } from '@/lib/utils'

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function addDays(isoDate: string, days: number): string {
  const d = new Date(isoDate + 'T00:00:00')
  d.setDate(d.getDate() + days)
  return iso(d)
}

/**
 * Tasks — one store, three projections (Todoist-style): List and Board group by
 * due date, Calendar lays the same rows onto a month grid. Toggling a checkbox
 * works identically in every view.
 */
export function TasksPage() {
  const today = iso(new Date())
  const [view, setView] = useState('list')

  return (
    <div className="w-full overflow-auto px-8 py-8">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">Tasks</h1>
        <Tabs value={view} onValueChange={setView}>
          <TabsList>
            <TabsTrigger value="list">List</TabsTrigger>
            <TabsTrigger value="board">Board</TabsTrigger>
            <TabsTrigger value="calendar">Calendar</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>
      {view === 'calendar' ? <CalendarView today={today} /> : <GroupedViews today={today} board={view === 'board'} />}
    </div>
  )
}

/** Shared bucketing for List and Board — same groups, different layout. */
function GroupedViews({ today, board }: { today: string; board: boolean }) {
  const { data: dated = [] } = useRangeTasks(addDays(today, -60), addDays(today, 90))
  const { data: someday = [] } = useSomedayTasks()

  const weekEnd = addDays(today, 7)
  const open = dated.filter((t) => t.status === 'OPEN')
  const groups: { label: string; tone?: 'danger' | 'muted'; tasks: Task[] }[] = [
    { label: 'Overdue', tone: 'danger', tasks: open.filter((t) => t.dueDate! < today) },
    { label: 'Today', tasks: open.filter((t) => t.dueDate === today) },
    { label: 'This week', tasks: open.filter((t) => t.dueDate! > today && t.dueDate! <= weekEnd) },
    { label: 'Later', tasks: open.filter((t) => t.dueDate! > weekEnd) },
    { label: 'Someday', tone: 'muted', tasks: someday },
    {
      label: 'Done',
      tone: 'muted',
      tasks: dated
        .filter((t) => t.status === 'DONE')
        .sort((a, b) => (b.completedAt ?? '').localeCompare(a.completedAt ?? ''))
        .slice(0, 10),
    },
  ]
  const visible = groups.filter((g) => g.tasks.length > 0)

  if (visible.length === 0) {
    return <p className="mt-8 text-sm text-muted-foreground">Chưa có task nào — Capture hoặc quick-add ở Today.</p>
  }

  if (!board) {
    return (
      <div className="mx-auto mt-6 max-w-3xl space-y-6">
        {visible.map((g) => (
          <section key={g.label}>
            <GroupLabel label={g.label} count={g.tasks.length} tone={g.tone} />
            <div className="divide-y">
              {g.tasks.map((t) => (
                <TaskLine key={t.id} task={t} overdue={g.label === 'Overdue'} />
              ))}
            </div>
          </section>
        ))}
      </div>
    )
  }

  return (
    <div className="mt-6 flex gap-4 overflow-x-auto pb-4">
      {visible.map((g) => (
        <div key={g.label} className="w-72 shrink-0 rounded-xl bg-muted/50 p-3">
          <GroupLabel label={g.label} count={g.tasks.length} tone={g.tone} />
          <div className="mt-2 space-y-2">
            {g.tasks.map((t) => (
              <Card key={t.id} className="gap-1 p-3">
                <TaskLine task={t} overdue={g.label === 'Overdue'} compact />
              </Card>
            ))}
          </div>
        </div>
      ))}
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

function TaskLine({ task, overdue, compact }: { task: Task; overdue?: boolean; compact?: boolean }) {
  const setDone = useSetTaskDone()
  const done = task.status === 'DONE'
  return (
    <div className={cn('flex items-center gap-3', compact ? 'py-0' : 'py-2.5')}>
      <Checkbox
        checked={done}
        onCheckedChange={() => setDone.mutate({ id: task.id, done: !done })}
        aria-label={task.title}
        className={cn('rounded-full', overdue && 'border-destructive')}
      />
      <span className={cn('min-w-0 flex-1 truncate text-sm', done && 'text-muted-foreground line-through')}>
        {task.title}
      </span>
      {task.dueDate && !compact && (
        <span className="flex shrink-0 items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground">
          {task.dueTime && <Clock className="size-3" />}
          {new Date(task.dueDate + 'T00:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
          {task.dueTime && ` · ${task.dueTime.slice(0, 5)}`}
        </span>
      )}
      {task.dueTime && compact && (
        <Badge variant="secondary" className="shrink-0">
          <Clock className="size-3" /> {task.dueTime.slice(0, 5)}
        </Badge>
      )}
    </div>
  )
}

function CalendarView({ today }: { today: string }) {
  const [monthStart, setMonthStart] = useState(() => {
    const d = new Date()
    return new Date(d.getFullYear(), d.getMonth(), 1)
  })

  // Monday-first grid covering the whole month.
  const gridStart = new Date(monthStart)
  gridStart.setDate(1 - ((monthStart.getDay() + 6) % 7))
  const cells: Date[] = Array.from({ length: 42 }, (_, i) => {
    const d = new Date(gridStart)
    d.setDate(gridStart.getDate() + i)
    return d
  })
  const weeks = cells.slice(35).some((d) => d.getMonth() === monthStart.getMonth()) ? cells : cells.slice(0, 35)

  const from = iso(weeks[0])
  const to = iso(weeks[weeks.length - 1])
  const { data: tasks = [] } = useRangeTasks(from, to)
  const byDate = new Map<string, Task[]>()
  for (const t of tasks) {
    if (!t.dueDate) continue
    byDate.set(t.dueDate, [...(byDate.get(t.dueDate) ?? []), t])
  }

  function shiftMonth(delta: number) {
    setMonthStart((m) => new Date(m.getFullYear(), m.getMonth() + delta, 1))
  }

  const monthLabel = monthStart.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })

  return (
    <div className="mt-6">
      <div className="mb-3 flex items-center justify-end gap-1">
        <Button size="icon" variant="ghost" onClick={() => shiftMonth(-1)} aria-label="Previous month">
          <ChevronLeft className="size-4" />
        </Button>
        <span className="w-36 text-center text-sm font-medium">{monthLabel}</span>
        <Button size="icon" variant="ghost" onClick={() => shiftMonth(1)} aria-label="Next month">
          <ChevronRight className="size-4" />
        </Button>
      </div>
      <div className="grid grid-cols-7 overflow-hidden rounded-xl border text-sm">
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => (
          <div key={d} className="border-b bg-muted/50 px-2 py-1.5 text-xs font-medium text-muted-foreground">
            {d}
          </div>
        ))}
        {weeks.map((d) => {
          const key = iso(d)
          const inMonth = d.getMonth() === monthStart.getMonth()
          const isToday = key === today
          const dayTasks = byDate.get(key) ?? []
          return (
            <div
              key={key}
              className={cn(
                'min-h-24 border-b border-r p-1.5 [&:nth-child(7n+8)]:border-l-0',
                !inMonth && 'bg-muted/30',
              )}
            >
              <div className="mb-1 flex justify-end">
                <span
                  className={cn(
                    'inline-flex size-6 items-center justify-center rounded-full text-xs',
                    isToday ? 'bg-primary font-semibold text-primary-foreground' : 'text-muted-foreground',
                    !inMonth && 'opacity-50',
                  )}
                >
                  {d.getDate()}
                </span>
              </div>
              <div className="space-y-1">
                {dayTasks.slice(0, 3).map((t) => (
                  <CalendarChip key={t.id} task={t} overdue={t.status === 'OPEN' && key < today} />
                ))}
                {dayTasks.length > 3 && (
                  <div className="px-1 text-xs text-muted-foreground">+{dayTasks.length - 3} more</div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function CalendarChip({ task, overdue }: { task: Task; overdue: boolean }) {
  const done = task.status === 'DONE'
  return (
    <div
      title={task.title}
      className={cn(
        'truncate rounded-md px-1.5 py-0.5 text-xs',
        done && 'bg-muted text-muted-foreground line-through',
        !done && overdue && 'bg-destructive/10 text-destructive',
        !done && !overdue && 'bg-primary/10 text-primary',
      )}
    >
      {task.dueTime ? `${task.dueTime.slice(0, 5)} ` : ''}
      {task.title}
    </div>
  )
}
