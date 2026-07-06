import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { components } from './api.gen'

type Schemas = components['schemas']

export type Project = Schemas['ProjectSummary']
export type Milestone = Schemas['MilestoneSummary']

export interface ProjectInput {
  name: string
  notes?: string
  disciplineId?: string
  startDate?: string
  targetDate?: string
}

async function listProjects(): Promise<Project[]> {
  const { data, error } = await api.GET('/api/projects')
  if (error) throw error
  return data ?? []
}

export function useProjects() {
  return useQuery({ queryKey: ['projects'], queryFn: listProjects })
}

function useInvalidateProjects() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['projects'] })
    queryClient.invalidateQueries({ queryKey: ['discipline-overview'] })
    queryClient.invalidateQueries({ queryKey: ['discipline-cards'] })
  }
}

export function useCreateProject() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async (body: ProjectInput) => {
      const { data, error } = await api.POST('/api/projects', { body })
      if (error) throw error
      return data as Project
    },
    onSuccess: invalidate,
  })
}

export function useUpdateProject() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ id, ...body }: ProjectInput & { id: string }) => {
      const { data, error } = await api.PUT('/api/projects/{id}', {
        params: { path: { id } },
        body,
      })
      if (error) throw error
      return data as Project
    },
    onSuccess: invalidate,
  })
}

export function useSetProjectDone() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ id, done }: { id: string; done: boolean }) => {
      const { data, error } = await api.PATCH('/api/projects/{id}/status', {
        params: { path: { id } },
        body: { done },
      })
      if (error) throw error
      return data as Project
    },
    onSuccess: invalidate,
  })
}

export function useDeleteProject() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async (id: string) => {
      const { error } = await api.DELETE('/api/projects/{id}', { params: { path: { id } } })
      if (error) throw error
    },
    onSuccess: invalidate,
  })
}

export function useAddMilestone() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ projectId, name, dueDate }: { projectId: string; name: string; dueDate?: string }) => {
      const { data, error } = await api.POST('/api/projects/{id}/milestones', {
        params: { path: { id: projectId } },
        body: { name, dueDate },
      })
      if (error) throw error
      return data as Project
    },
    onSuccess: invalidate,
  })
}

export function useToggleMilestone() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ projectId, milestoneId }: { projectId: string; milestoneId: string }) => {
      const { data, error } = await api.PATCH('/api/projects/{id}/milestones/{milestoneId}/toggle', {
        params: { path: { id: projectId, milestoneId } },
      })
      if (error) throw error
      return data as Project
    },
    onSuccess: invalidate,
  })
}

export function useRemoveMilestone() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ projectId, milestoneId }: { projectId: string; milestoneId: string }) => {
      const { data, error } = await api.DELETE('/api/projects/{id}/milestones/{milestoneId}', {
        params: { path: { id: projectId, milestoneId } },
      })
      if (error) throw error
      return data as Project
    },
    onSuccess: invalidate,
  })
}

/** The project's agenda: every task attached to it. */
export function useProjectTasks(projectId: string | null) {
  return useQuery({
    queryKey: ['project-tasks', projectId],
    enabled: !!projectId,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/tasks/by-project', {
        params: { query: { projectId: projectId as string } },
      })
      if (error) throw error
      return data ?? []
    },
  })
}

/** Attach/detach a task (null detaches). */
export function useSetTaskProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ taskId, projectId }: { taskId: string; projectId: string | null }) => {
      const { data, error } = await api.PATCH('/api/tasks/{id}/project', {
        params: { path: { id: taskId } },
        body: { projectId: projectId ?? undefined },
      })
      if (error) throw error
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-tasks'] })
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
    },
  })
}
