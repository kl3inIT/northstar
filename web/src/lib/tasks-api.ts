import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createTask as createTaskRequest,
  deleteTask as deleteTaskRequest,
  listOpenTasksByDiscipline,
  listSomedayTasks,
  listTasksByDateRange,
  listTodayTasks,
  listUpcomingTasks,
  setTaskPlannedDate,
  setTaskStatus,
  updateTask as updateTaskRequest,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'
import type { TaskRequest, TaskSummary } from './hey-api'

export type TaskStatus = 'OPEN' | 'DONE'

/** Read model for task lists; api records always serialize every field. */
export type Task = {
  id: string
  title: string
  notes: string | null
  status: TaskStatus
  dueDate: string | null
  dueTime: string | null
  /** Do-vs-due "do" day (Things-style star); independent of the deadline. */
  plannedDate: string | null
  completedAt: string | null
  createdAt: string
  disciplineId: string | null
}

export type TaskInput = TaskRequest

const TZ = Intl.DateTimeFormat().resolvedOptions().timeZone
const tzHeaders = { 'X-Timezone': TZ }

function toTask(t: TaskSummary): Task {
  return {
    id: t.id ?? '',
    title: t.title ?? '',
    notes: t.notes ?? null,
    status: (t.status ?? 'OPEN') as TaskStatus,
    dueDate: t.dueDate ?? null,
    dueTime: t.dueTime ?? null,
    plannedDate: t.plannedDate ?? null,
    completedAt: t.completedAt ?? null,
    createdAt: t.createdAt ?? '',
    disciplineId: t.disciplineId ?? null,
  }
}

export async function todayTasks(): Promise<Task[]> {
  const data = dataOrThrow(await listTodayTasks({ headers: tzHeaders }))
  return data.map(toTask)
}

export async function upcomingTasks(days = 7): Promise<Task[]> {
  const data = dataOrThrow(await listUpcomingTasks({
    headers: tzHeaders,
    query: { days },
  }))
  return data.map(toTask)
}

export async function rangeTasks(from: string, to: string): Promise<Task[]> {
  const data = dataOrThrow(await listTasksByDateRange({ query: { from, to } }))
  return data.map(toTask)
}

export async function somedayTasks(): Promise<Task[]> {
  const data = dataOrThrow(await listSomedayTasks())
  return data.map(toTask)
}

/** Open tasks of one discipline — the agenda inside a study block's details. */
export async function openTasksByDiscipline(disciplineId: string): Promise<Task[]> {
  const data = dataOrThrow(await listOpenTasksByDiscipline({ query: { disciplineId } }))
  return data.map(toTask)
}

export async function createTask(body: TaskInput): Promise<Task> {
  return toTask(dataOrThrow(await createTaskRequest({ body })))
}

export async function updateTask(id: string, body: TaskInput): Promise<Task> {
  return toTask(dataOrThrow(await updateTaskRequest({
    path: { id },
    body,
  })))
}

export async function setTaskDone(id: string, done: boolean): Promise<Task> {
  return toTask(dataOrThrow(await setTaskStatus({
    path: { id },
    body: { done },
  })))
}

/** Star/unstar the "do" day (null clears); never moves the deadline. */
export async function setTaskPlanned(id: string, plannedDate: string | null): Promise<Task> {
  return toTask(dataOrThrow(await setTaskPlannedDate({
    path: { id },
    body: { plannedDate: plannedDate ?? undefined },
  })))
}

export async function deleteTask(id: string): Promise<void> {
  voidOrThrow(await deleteTaskRequest({ path: { id } }))
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

export function useSetTaskPlanned() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, plannedDate }: { id: string; plannedDate: string | null }) =>
      setTaskPlanned(id, plannedDate),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] }),
  })
}
