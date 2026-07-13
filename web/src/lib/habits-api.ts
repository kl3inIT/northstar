import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  clearHabitCheckIn,
  createHabit,
  getHabitInsights,
  listHabits,
  listTodayHabits,
  pauseHabit,
  resumeHabit,
  setHabitArchived,
  setHabitCheckIn,
  updateHabit,
} from './hey-api'
import { dataOrThrow } from './hey-api-result'
import type {
  HabitCheckInRequest,
  HabitInsights,
  HabitRequest,
  HabitSummary,
  HabitTodaySummary,
} from './hey-api'

export type Habit = HabitSummary
export type TodayHabit = HabitTodaySummary
export type HabitInput = HabitRequest
export type HabitCheckInStatus = HabitCheckInRequest['status']
export type { HabitInsights }

const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
const tzHeaders = { 'X-Timezone': timezone }

function useInvalidateHabits() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['habits'] })
}

export function useTodayHabits() {
  return useQuery({
    queryKey: ['habits', 'today'],
    queryFn: async () => dataOrThrow(await listTodayHabits({ headers: tzHeaders })),
  })
}

export function useHabits(includeArchived = false) {
  return useQuery({
    queryKey: ['habits', 'list', includeArchived],
    queryFn: async () => dataOrThrow(await listHabits({
      query: { includeArchived },
      headers: tzHeaders,
    })),
  })
}

export function useHabitInsights(from: string, to: string) {
  return useQuery({
    queryKey: ['habits', 'insights', from, to],
    queryFn: async () => dataOrThrow(await getHabitInsights({
      query: { from, to, includeArchived: false },
      headers: tzHeaders,
    })),
  })
}

export function useSaveHabit() {
  const invalidate = useInvalidateHabits()
  return useMutation({
    mutationFn: async ({ id, input }: { id?: string; input: HabitInput }) => id
      ? dataOrThrow(await updateHabit({ path: { id }, body: input, headers: tzHeaders }))
      : dataOrThrow(await createHabit({ body: input, headers: tzHeaders })),
    onSuccess: invalidate,
  })
}

export function useSetHabitCheckIn() {
  const invalidate = useInvalidateHabits()
  return useMutation({
    mutationFn: async ({ id, date, status }: { id: string; date: string; status: HabitCheckInStatus }) =>
      dataOrThrow(await setHabitCheckIn({ path: { id, date }, body: { status }, headers: tzHeaders })),
    onSuccess: invalidate,
  })
}

export function useClearHabitCheckIn() {
  const invalidate = useInvalidateHabits()
  return useMutation({
    mutationFn: async ({ id, date }: { id: string; date: string }) =>
      dataOrThrow(await clearHabitCheckIn({ path: { id, date }, headers: tzHeaders })),
    onSuccess: invalidate,
  })
}

export function usePauseHabit() {
  const invalidate = useInvalidateHabits()
  return useMutation({
    mutationFn: async ({ id, date }: { id: string; date?: string }) =>
      dataOrThrow(await pauseHabit({ path: { id }, body: date ? { date } : {}, headers: tzHeaders })),
    onSuccess: invalidate,
  })
}

export function useResumeHabit() {
  const invalidate = useInvalidateHabits()
  return useMutation({
    mutationFn: async ({ id, date }: { id: string; date?: string }) =>
      dataOrThrow(await resumeHabit({ path: { id }, body: date ? { date } : {}, headers: tzHeaders })),
    onSuccess: invalidate,
  })
}

export function useSetHabitArchived() {
  const invalidate = useInvalidateHabits()
  return useMutation({
    mutationFn: async ({ id, archived }: { id: string; archived: boolean }) =>
      dataOrThrow(await setHabitArchived({ path: { id }, body: { archived }, headers: tzHeaders })),
    onSuccess: invalidate,
  })
}
