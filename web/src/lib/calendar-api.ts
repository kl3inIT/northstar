import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { components } from './api.gen'
import type { IEvent } from '@/features/calendar/interfaces'
import type { TEventColor } from '@/features/calendar/types'

type Schemas = components['schemas']

export type EventColor = Schemas['CalendarEventSummary']['color']

// Recurring series expand server-side at the local time-of-day of this zone.
const tzHeaders = { 'X-Timezone': Intl.DateTimeFormat().resolvedOptions().timeZone }

export interface CalendarEventInput {
  title: string
  notes?: string
  startAt: string
  endAt: string
  allDay: boolean
  color: EventColor
  disciplineId?: string
  /** RFC 5545 subset (FREQ=DAILY|WEEKLY…); omit for one-off events. */
  rrule?: string
}

/**
 * API record → the UI model the vendored calendar components render. A
 * recurring occurrence shares its master's server id, so its client id is
 * made unique (`id@startAt`) and the server id moves to masterId.
 */
function toEvent(e: Schemas['CalendarEventSummary']): IEvent {
  const recurring = !!e.rrule
  return {
    id: recurring ? `${e.id}@${e.startAt}` : e.id,
    title: e.title,
    description: e.notes ?? '',
    startDate: e.startAt,
    endDate: e.endAt,
    allDay: e.allDay ?? false,
    color: e.color.toLowerCase() as TEventColor,
    disciplineId: e.disciplineId,
    kind: 'event',
    rrule: e.rrule ?? undefined,
    masterId: recurring ? e.id : undefined,
    occurrenceStart: recurring ? e.startAt : undefined,
    createdAt: e.createdAt,
  }
}

export async function rangeEvents(from: string, to: string): Promise<IEvent[]> {
  const { data, error } = await api.GET('/api/calendar/events', {
    params: { query: { from, to } },
    headers: tzHeaders,
  })
  if (error) throw error
  return (data ?? []).map(toEvent)
}

/** The raw master row — what the "edit series" form prefills from. */
export async function getEvent(id: string): Promise<Schemas['CalendarEventSummary']> {
  const { data, error } = await api.GET('/api/calendar/events/{id}', { params: { path: { id } } })
  if (error) throw error
  return data as Schemas['CalendarEventSummary']
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

/** Without occurrenceStart: delete the event / whole series. With it: "chỉ buổi này". */
export async function deleteEvent(id: string, occurrenceStart?: string): Promise<void> {
  const { error } = await api.DELETE('/api/calendar/events/{id}', {
    params: { path: { id }, query: occurrenceStart ? { occurrenceStart } : undefined },
  })
  if (error) throw error
}

export function useRangeEvents(from: string, to: string) {
  return useQuery({ queryKey: ['calendar-events', from, to], queryFn: () => rangeEvents(from, to) })
}

/** Master row for the edit-series form; enabled only while that form is open. */
export function useEventMaster(id: string | undefined) {
  return useQuery({
    queryKey: ['calendar-event', id],
    queryFn: () => getEvent(id!),
    enabled: !!id,
  })
}

function useInvalidateEvents() {
  const queryClient = useQueryClient()
  return () =>
    queryClient.invalidateQueries({
      predicate: q => q.queryKey[0] === 'calendar-events' || q.queryKey[0] === 'calendar-event',
    })
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
  return useMutation({
    mutationFn: ({ id, occurrenceStart }: { id: string; occurrenceStart?: string }) =>
      deleteEvent(id, occurrenceStart),
    onSuccess: invalidate,
  })
}

/** Drag of one buổi in a series: cancel that occurrence, re-create it standalone. */
export function useDetachOccurrence() {
  const invalidate = useInvalidateEvents()
  return useMutation({
    mutationFn: async ({ masterId, occurrenceStart, body }: { masterId: string; occurrenceStart: string; body: CalendarEventInput }) => {
      await deleteEvent(masterId, occurrenceStart)
      return createEvent(body)
    },
    onSuccess: invalidate,
  })
}
