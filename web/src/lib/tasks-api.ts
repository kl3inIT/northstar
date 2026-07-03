import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { components } from './api.gen'

type Schemas = components['schemas']

export type TaskStatus = 'OPEN' | 'DONE'

/** Read model for task lists; api records always serialize every field. */
export type Task = {
  id: string
  title: string
  notes: string | null
  status: TaskStatus
  dueDate: string | null
  dueTime: string | null
  completedAt: string | null
  createdAt: string
}

export type TaskInput = Schemas['TaskRequest']

const TZ = Intl.DateTimeFormat().resolvedOptions().timeZone
const tzHeaders = { 'X-Timezone': TZ }

function toTask(t: Schemas['TaskSummary']): Task {
  return {
    id: t.id ?? '',
    title: t.title ?? '',
    notes: t.notes ?? null,
    status: (t.status ?? 'OPEN') as TaskStatus,
    dueDate: t.dueDate ?? null,
    dueTime: t.dueTime ?? null,
    completedAt: t.completedAt ?? null,
    createdAt: t.createdAt ?? '',
  }
}

export async function todayTasks(): Promise<Task[]> {
  const { data, error } = await api.GET('/api/tasks/today', { headers: tzHeaders })
  if (error) throw error
  return (data ?? []).map(toTask)
}

export async function upcomingTasks(days = 7): Promise<Task[]> {
  const { data, error } = await api.GET('/api/tasks/upcoming', {
    headers: tzHeaders,
    params: { query: { days } },
  })
  if (error) throw error
  return (data ?? []).map(toTask)
}

export async function createTask(body: TaskInput): Promise<Task> {
  const { data, error } = await api.POST('/api/tasks', { body })
  if (error) throw error
  return toTask(data as Schemas['TaskSummary'])
}

export async function setTaskDone(id: string, done: boolean): Promise<Task> {
  const { data, error } = await api.PATCH('/api/tasks/{id}/status', {
    params: { path: { id } },
    body: { done },
  })
  if (error) throw error
  return toTask(data as Schemas['TaskSummary'])
}

export async function deleteTask(id: string): Promise<void> {
  const { error } = await api.DELETE('/api/tasks/{id}', { params: { path: { id } } })
  if (error) throw error
}

export function useTodayTasks() {
  return useQuery({ queryKey: ['tasks', 'today'], queryFn: todayTasks })
}

export function useUpcomingTasks(days = 7) {
  return useQuery({ queryKey: ['tasks', 'upcoming', days], queryFn: () => upcomingTasks(days) })
}

export function useCreateTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createTask,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] }),
  })
}

export function useSetTaskDone() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, done }: { id: string; done: boolean }) => setTaskDone(id, done),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] }),
  })
}
