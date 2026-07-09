import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addProjectMilestone,
  createProject,
  deleteProject,
  listProjects as listProjectsRequest,
  listTasksByProject,
  removeProjectMilestone,
  setProjectStatus,
  setTaskProject,
  toggleProjectMilestone,
  updateProject,
} from './hey-api'
import { dataOrThrow, voidOrThrow } from './hey-api-result'
import type { MilestoneSummary, ProjectSummary } from './hey-api'

export type Project = ProjectSummary
export type Milestone = MilestoneSummary

export interface ProjectInput {
  name: string
  notes?: string
  disciplineId?: string
  startDate?: string
  targetDate?: string
}

async function listProjects(): Promise<Project[]> {
  return dataOrThrow(await listProjectsRequest())
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
      return dataOrThrow(await createProject({ body }))
    },
    onSuccess: invalidate,
  })
}

export function useUpdateProject() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ id, ...body }: ProjectInput & { id: string }) => {
      return dataOrThrow(await updateProject({
        path: { id },
        body,
      }))
    },
    onSuccess: invalidate,
  })
}

export function useSetProjectDone() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ id, done }: { id: string; done: boolean }) => {
      return dataOrThrow(await setProjectStatus({
        path: { id },
        body: { done },
      }))
    },
    onSuccess: invalidate,
  })
}

export function useDeleteProject() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async (id: string) => {
      voidOrThrow(await deleteProject({ path: { id } }))
    },
    onSuccess: invalidate,
  })
}

export function useAddMilestone() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ projectId, name, dueDate }: { projectId: string; name: string; dueDate?: string }) => {
      return dataOrThrow(await addProjectMilestone({
        path: { id: projectId },
        body: { name, dueDate },
      }))
    },
    onSuccess: invalidate,
  })
}

export function useToggleMilestone() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ projectId, milestoneId }: { projectId: string; milestoneId: string }) => {
      return dataOrThrow(await toggleProjectMilestone({ path: { id: projectId, milestoneId } }))
    },
    onSuccess: invalidate,
  })
}

export function useRemoveMilestone() {
  const invalidate = useInvalidateProjects()
  return useMutation({
    mutationFn: async ({ projectId, milestoneId }: { projectId: string; milestoneId: string }) => {
      return dataOrThrow(await removeProjectMilestone({ path: { id: projectId, milestoneId } }))
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
      return dataOrThrow(await listTasksByProject({ query: { projectId: projectId as string } }))
    },
  })
}

/** Attach/detach a task (null detaches). */
export function useSetTaskProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ taskId, projectId }: { taskId: string; projectId: string | null }) => {
      return dataOrThrow(await setTaskProject({
        path: { id: taskId },
        body: { projectId: projectId ?? undefined },
      }))
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-tasks'] })
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
    },
  })
}
