import { Clock } from 'lucide-react'
import { useState } from 'react'
import { Badge } from '@/components/ui/badge'
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
 * Tasks — one store, two projections (Todoist-style): List and Board group by
 * due date. The time view lives on the Calendar page, which overlays these
 * same tasks onto the real calendar. Toggling a checkbox works identically in
 * every view.
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
          </TabsList>
        </Tabs>
      </div>
      <GroupedViews today={today} board={view === 'board'} />
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
