import { Clock, Plus } from 'lucide-react'
import { useState } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { ReviewCard } from '@/features/alignment/review-card'
import { cn } from '@/lib/utils'
import {
  useCreateTask,
  useSetTaskDone,
  useTodayTasks,
  useUpcomingTasks,
  type Task,
} from '@/lib/tasks-api'

function localToday(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function headerDate(): string {
  return new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' })
}

function dueChip(task: Task): string | null {
  if (!task.dueDate) return null
  const date = new Date(task.dueDate + 'T00:00:00')
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

/** Today — overdue + due-today (+ done today) with a plain, no-AI quick-add. */
export function TodayPage() {
  const today = localToday()
  const { data: todayList = [], isLoading } = useTodayTasks()
  const { data: upcoming = [] } = useUpcomingTasks(7)
  const createTask = useCreateTask()
  const setDone = useSetTaskDone()
  const [title, setTitle] = useState('')

  const overdue = todayList.filter((t) => t.status === 'OPEN' && t.dueDate !== null && t.dueDate < today)
  const dueToday = todayList.filter((t) => t.status === 'OPEN' && t.dueDate === today)
  const doneToday = todayList.filter((t) => t.status === 'DONE')

  function onQuickAdd(e: React.KeyboardEvent) {
    if (e.key !== 'Enter') return
    const value = title.trim()
    if (!value) return
    setTitle('')
    createTask.mutate({ title: value, dueDate: today })
  }

  function toggle(task: Task) {
    setDone.mutate({ id: task.id, done: task.status === 'OPEN' })
  }

  return (
    <div className="mx-auto w-full max-w-3xl overflow-auto px-8 py-8">
      <div className="flex items-baseline gap-3">
        <h1 className="text-3xl font-bold tracking-tight">Today</h1>
        <span className="text-sm text-muted-foreground">{headerDate()}</span>
      </div>

      <div className="relative mt-5">
        <Plus className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onKeyDown={onQuickAdd}
          placeholder="Add a task… (Enter to save)"
          className="h-11 pl-9"
        />
      </div>

      {isLoading ? (
        <div className="mt-6 space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : (
        <div className="mt-6 space-y-6">
          {overdue.length > 0 && (
            <Section label="Overdue" count={overdue.length} tone="danger">
              {overdue.map((t) => (
                <TaskRow key={t.id} task={t} chip={dueChip(t)} overdue onToggle={() => toggle(t)} />
              ))}
            </Section>
          )}
          <Section label="Today" count={dueToday.length + doneToday.length}>
            {dueToday.length === 0 && doneToday.length === 0 && (
              <p className="py-2 text-sm text-muted-foreground">
                Chưa có task cho hôm nay — thêm ở ô trên hoặc Capture (⌃⇧K).
              </p>
            )}
            {dueToday.map((t) => (
              <TaskRow
                key={t.id}
                task={t}
                chip={t.dueTime ? t.dueTime.slice(0, 5) : null}
                timeChip
                onToggle={() => toggle(t)}
              />
            ))}
            {doneToday.map((t) => (
              <TaskRow key={t.id} task={t} chip={null} onToggle={() => toggle(t)} />
            ))}
          </Section>
          {upcoming.length > 0 && (
            <Section label="Upcoming" muted>
              {upcoming.map((t) => (
                <TaskRow key={t.id} task={t} chip={dueChip(t)} dimmed onToggle={() => toggle(t)} />
              ))}
            </Section>
          )}
          <ReviewCard />
        </div>
      )}
    </div>
  )
}

function Section({
  label,
  count,
  tone,
  muted,
  children,
}: {
  label: string
  count?: number
  tone?: 'danger'
  muted?: boolean
  children: React.ReactNode
}) {
  return (
    <section>
      <div className="mb-1 flex items-center gap-2">
        <h2
          className={cn(
            'text-sm font-semibold',
            tone === 'danger' && 'text-destructive',
            muted && 'text-muted-foreground',
          )}
        >
          {label}
        </h2>
        {count !== undefined && count > 0 && (
          <span
            className={cn(
              'rounded-full px-1.5 text-xs',
              tone === 'danger' ? 'bg-destructive/10 text-destructive' : 'bg-muted text-muted-foreground',
            )}
          >
            {count}
          </span>
        )}
      </div>
      <div className="divide-y">{children}</div>
    </section>
  )
}

function TaskRow({
  task,
  chip,
  timeChip,
  overdue,
  dimmed,
  onToggle,
}: {
  task: Task
  chip: string | null
  timeChip?: boolean
  overdue?: boolean
  dimmed?: boolean
  onToggle: () => void
}) {
  const done = task.status === 'DONE'
  return (
    <div className={cn('flex items-center gap-3 py-2.5', dimmed && 'opacity-60')}>
      <Checkbox
        checked={done}
        onCheckedChange={onToggle}
        aria-label={task.title}
        className={cn('rounded-full', overdue && 'border-destructive')}
      />
      <span className={cn('flex-1 truncate text-sm', done && 'text-muted-foreground line-through')}>
        {task.title}
      </span>
      {chip && (
        <span className="flex shrink-0 items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground">
          {timeChip && <Clock className="size-3" />}
          {chip}
        </span>
      )}
    </div>
  )
}
