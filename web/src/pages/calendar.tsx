import { useMemo, useState } from 'react'
import { endOfDay, endOfMonth, endOfWeek, endOfYear, format, startOfDay, startOfMonth, startOfWeek, startOfYear } from 'date-fns'
import { CalendarProvider } from '@/features/calendar/contexts/calendar-context'
import { ClientContainer } from '@/features/calendar/components/client-container'
import { useRangeEvents } from '@/lib/calendar-api'
import { useDisciplines } from '@/lib/disciplines-api'
import { useRangeTasks } from '@/lib/tasks-api'
import type { IEvent } from '@/features/calendar/interfaces'
import type { TCalendarView, TEventColor } from '@/features/calendar/types'
import type { Task } from '@/lib/tasks-api'

const WEEK_STARTS_ON = 1 // Monday, VN convention

/** Task chip color encodes urgency, not preference: overdue > today > future > done. */
function taskColor(task: Task, today: string): TEventColor {
  if (task.status === 'DONE') return 'gray'
  if (task.dueDate! < today) return 'red'
  if (task.dueDate === today) return 'orange'
  return 'blue'
}

/**
 * A dated task rendered as a read-only all-day chip (Google-Calendar style).
 * Overdue open tasks roll forward onto TODAY — the user must see the debt
 * where they are looking, not on a day already gone.
 */
function taskToEvent(task: Task, today: string): IEvent {
  const overdue = task.status === 'OPEN' && task.dueDate! < today
  const day = overdue ? today : task.dueDate!
  const time = task.dueTime ? `${task.dueTime.slice(0, 5)} ` : ''
  const late = overdue ? `(trễ ${format(new Date(task.dueDate! + 'T00:00:00'), 'd/M')}) ` : ''
  return {
    id: task.id,
    title: `${late}${time}${task.title}`,
    description: task.notes ?? '',
    startDate: `${day}T00:00:00`,
    endDate: `${day}T23:59:59`,
    allDay: true,
    color: taskColor(task, today),
    kind: 'task',
    taskDone: task.status === 'DONE',
    task: { id: task.id, title: task.title, notes: task.notes, dueTime: task.dueTime, disciplineId: task.disciplineId },
  }
}

/**
 * Calendar — time-blocked events (own store) overlaid with dated tasks.
 * The page owns view + date so it can fetch exactly the visible window;
 * everything below reads them from CalendarContext.
 */
export function CalendarPage() {
  const [view, setView] = useState<TCalendarView>('month')
  const [selectedDate, setSelectedDate] = useState(() => new Date())

  // Visible window per view (month grid spills into adjacent weeks).
  const window = useMemo(() => {
    switch (view) {
      case 'year':
        return { start: startOfYear(selectedDate), end: endOfYear(selectedDate) }
      case 'week':
        return { start: startOfWeek(selectedDate, { weekStartsOn: WEEK_STARTS_ON }), end: endOfWeek(selectedDate, { weekStartsOn: WEEK_STARTS_ON }) }
      case 'day':
        return { start: startOfDay(selectedDate), end: endOfDay(selectedDate) }
      default:
        return {
          start: startOfWeek(startOfMonth(selectedDate), { weekStartsOn: WEEK_STARTS_ON }),
          end: endOfWeek(endOfMonth(selectedDate), { weekStartsOn: WEEK_STARTS_ON }),
        }
    }
  }, [view, selectedDate])

  const { data: events = [] } = useRangeEvents(window.start.toISOString(), window.end.toISOString())
  const { data: disciplines = [] } = useDisciplines()
  // Overdue open tasks roll onto today, so the task fetch starts well before
  // the visible window to catch old debt.
  const { data: tasks = [] } = useRangeTasks(
    format(new Date(window.start.getTime() - 90 * 86_400_000), 'yyyy-MM-dd'),
    format(window.end, 'yyyy-MM-dd'),
  )

  const merged = useMemo(() => {
    const today = format(new Date(), 'yyyy-MM-dd')
    const byId = new Map(disciplines.map(d => [d.id, d.name]))
    const enriched = events.map(e => (e.disciplineId ? { ...e, disciplineName: byId.get(e.disciplineId) } : e))
    const windowStart = format(window.start, 'yyyy-MM-dd')
    const chips = tasks
      .filter(t => t.dueDate)
      // Old DONE tasks fetched by the widened range don't belong on screen.
      .filter(t => t.dueDate! >= windowStart || (t.status === 'OPEN' && t.dueDate! < today))
      .map(t => taskToEvent(t, today))
    return [...enriched, ...chips]
  }, [events, tasks, disciplines, window.start])

  return (
    <div className="flex w-full flex-col overflow-auto p-6">
      <CalendarProvider
        events={merged}
        selectedDate={selectedDate}
        onSelectedDateChange={setSelectedDate}
        view={view}
        onViewChange={setView}
      >
        <ClientContainer />
      </CalendarProvider>
    </div>
  )
}
