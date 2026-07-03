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
  disciplineId: string | null
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
    disciplineId: t.disciplineId ?? null,
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

export async function rangeTasks(from: string, to: string): Promise<Task[]> {
  const { data, error } = await api.GET('/api/tasks/range', {
    params: { query: { from, to } },
  })
  if (error) throw error
  return (data ?? []).map(toTask)
}

export async function somedayTasks(): Promise<Task[]> {
  const { data, error } = await api.GET('/api/tasks/someday')
  if (error) throw error
  return (data ?? []).map(toTask)
}

/** Open tasks of one discipline — the agenda inside a study block's details. */
export async function openTasksByDiscipline(disciplineId: string): Promise<Task[]> {
  const { data, error } = await api.GET('/api/tasks/open', {
    params: { query: { disciplineId } },
  })
  if (error) throw error
  return (data ?? []).map(toTask)
}

export async function createTask(body: TaskInput): Promise<Task> {
  const { data, error } = await api.POST('/api/tasks', { body })
  if (error) throw error
  return toTask(data as Schemas['TaskSummary'])
}

export async function updateTask(id: string, body: TaskInput): Promise<Task> {
  const { data, error } = await api.PUT('/api/tasks/{id}', {
    params: { path: { id } },
    body,
  })
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

export function useRangeTasks(from: string, to: string) {
  return useQuery({ queryKey: ['tasks', 'range', from, to], queryFn: () => rangeTasks(from, to) })
}

export function useSomedayTasks() {
  return useQuery({ queryKey: ['tasks', 'someday'], queryFn: somedayTasks })
}

export function useOpenTasksByDiscipline(disciplineId: string | undefined) {
  return useQuery({
    queryKey: ['tasks', 'open', disciplineId],
    queryFn: () => openTasksByDiscipline(disciplineId!),
    enabled: !!disciplineId,
  })
}

export function useCreateTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createTask,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] }),
  })
}

export function useUpdateTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: TaskInput }) => updateTask(id, body),
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
