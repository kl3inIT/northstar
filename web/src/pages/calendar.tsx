import { useMemo, useState } from 'react'
import { endOfDay, endOfMonth, endOfWeek, endOfYear, format, startOfDay, startOfMonth, startOfWeek, startOfYear } from 'date-fns'
import { CalendarProvider } from '@/features/calendar/contexts/calendar-context'
import { ClientContainer } from '@/features/calendar/components/client-container'
import { useRangeEvents } from '@/lib/calendar-api'
import { useRangeTasks } from '@/lib/tasks-api'
import type { IEvent } from '@/features/calendar/interfaces'
import type { TCalendarView } from '@/features/calendar/types'
import type { Task } from '@/lib/tasks-api'

/** A dated task rendered as a read-only all-day chip (Google-Calendar style). */
function taskToEvent(task: Task): IEvent {
  const day = task.dueDate!
  const time = task.dueTime ? `${task.dueTime.slice(0, 5)} ` : ''
  return {
    id: task.id,
    title: `${time}${task.title}`,
    description: task.notes ?? '',
    startDate: `${day}T00:00:00`,
    endDate: `${day}T23:59:59`,
    allDay: true,
    color: 'gray',
    kind: 'task',
    taskDone: task.status === 'DONE',
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
        return { start: startOfWeek(selectedDate), end: endOfWeek(selectedDate) }
      case 'day':
        return { start: startOfDay(selectedDate), end: endOfDay(selectedDate) }
      default:
        return { start: startOfWeek(startOfMonth(selectedDate)), end: endOfWeek(endOfMonth(selectedDate)) }
    }
  }, [view, selectedDate])

  const { data: events = [] } = useRangeEvents(window.start.toISOString(), window.end.toISOString())
  const { data: tasks = [] } = useRangeTasks(format(window.start, 'yyyy-MM-dd'), format(window.end, 'yyyy-MM-dd'))

  const merged = useMemo(() => [...events, ...tasks.filter(t => t.dueDate).map(taskToEvent)], [events, tasks])

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
