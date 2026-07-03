import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { components } from './api.gen'
import type { IEvent } from '@/features/calendar/interfaces'
import type { TEventColor } from '@/features/calendar/types'

type Schemas = components['schemas']

export type EventColor = Schemas['CalendarEventSummary']['color']

export interface CalendarEventInput {
  title: string
  notes?: string
  startAt: string
  endAt: string
  allDay: boolean
  color: EventColor
  disciplineId?: string
}

/** API record → the UI model the vendored calendar components render. */
function toEvent(e: Schemas['CalendarEventSummary']): IEvent {
  return {
    id: e.id,
    title: e.title,
    description: e.notes ?? '',
    startDate: e.startAt,
    endDate: e.endAt,
    allDay: e.allDay ?? false,
    color: e.color.toLowerCase() as TEventColor,
    disciplineId: e.disciplineId,
    kind: 'event',
  }
}

export async function rangeEvents(from: string, to: string): Promise<IEvent[]> {
  const { data, error } = await api.GET('/api/calendar/events', {
    params: { query: { from, to } },
  })
  if (error) throw error
  return (data ?? []).map(toEvent)
}

export async function createEvent(body: CalendarEventInput): Promise<IEvent> {
  const { data, error } = await api.POST('/api/calendar/events', { body })
  if (error) throw error
  return toEvent(data as Schemas['CalendarEventSummary'])
}

export async function updateEvent(id: string, body: CalendarEventInput): Promise<IEvent> {
  const { data, error } = await api.PUT('/api/calendar/events/{id}', {
    params: { path: { id } },
    body,
  })
  if (error) throw error
  return toEvent(data as Schemas['CalendarEventSummary'])
}

export async function rescheduleEvent(id: string, startAt: string, endAt: string): Promise<IEvent> {
  const { data, error } = await api.PATCH('/api/calendar/events/{id}/schedule', {
    params: { path: { id } },
    body: { startAt, endAt },
  })
  if (error) throw error
  return toEvent(data as Schemas['CalendarEventSummary'])
}

export async function deleteEvent(id: string): Promise<void> {
  const { error } = await api.DELETE('/api/calendar/events/{id}', { params: { path: { id } } })
  if (error) throw error
}

export function useRangeEvents(from: string, to: string) {
  return useQuery({ queryKey: ['calendar-events', from, to], queryFn: () => rangeEvents(from, to) })
}

function useInvalidateEvents() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['calendar-events'] })
}

export function useCreateEvent() {
  const invalidate = useInvalidateEvents()
  return useMutation({ mutationFn: createEvent, onSuccess: invalidate })
}

export function useUpdateEventMutation() {
  const invalidate = useInvalidateEvents()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: CalendarEventInput }) => updateEvent(id, body),
    onSuccess: invalidate,
  })
}

export function useRescheduleEvent() {
  const invalidate = useInvalidateEvents()
  return useMutation({
    mutationFn: ({ id, startAt, endAt }: { id: string; startAt: string; endAt: string }) =>
      rescheduleEvent(id, startAt, endAt),
    onSuccess: invalidate,
  })
}

export function useDeleteEvent() {
  const invalidate = useInvalidateEvents()
  return useMutation({ mutationFn: deleteEvent, onSuccess: invalidate })
}
