import { format, parseISO, subDays } from 'date-fns'
import type { Habit, HabitInput, TodayHabit } from '@/lib/habits-api'

export const WEEKDAYS = [
  ['MONDAY', 'M'],
  ['TUESDAY', 'T'],
  ['WEDNESDAY', 'W'],
  ['THURSDAY', 'T'],
  ['FRIDAY', 'F'],
  ['SATURDAY', 'S'],
  ['SUNDAY', 'S'],
] as const

export type Weekday = HabitInput['days'][number]
export type HabitColor = HabitInput['color']

export const HABIT_COLORS: Array<{ value: HabitColor; className: string; label: string }> = [
  { value: 'GREEN', className: 'bg-emerald-500', label: 'Green' },
  { value: 'BLUE', className: 'bg-blue-500', label: 'Blue' },
  { value: 'PURPLE', className: 'bg-violet-500', label: 'Purple' },
  { value: 'ORANGE', className: 'bg-orange-500', label: 'Orange' },
  { value: 'YELLOW', className: 'bg-amber-400', label: 'Yellow' },
  { value: 'RED', className: 'bg-rose-500', label: 'Red' },
  { value: 'GRAY', className: 'bg-zinc-500', label: 'Gray' },
]

export const COLOR_DOT: Record<HabitColor, string> = Object.fromEntries(
  HABIT_COLORS.map(({ value, className }) => [value, className]),
) as Record<HabitColor, string>

export function localDate(date = new Date()): string {
  return format(date, 'yyyy-MM-dd')
}

export function scheduleLabel(habit: Habit): string {
  if (habit.schedule.frequencyType === 'WEEKLY_TARGET') {
    return `${habit.schedule.weeklyTarget} times a week`
  }
  if (habit.schedule.days.length === 7) return 'Every day'
  return habit.schedule.days.map((day) => day.slice(0, 3).toLowerCase()).join(', ')
}

export function defaultHabitInput(): HabitInput {
  return {
    title: '',
    color: 'GREEN',
    frequencyType: 'ON_DAYS',
    days: WEEKDAYS.map(([value]) => value),
    weeklyTarget: 3,
  }
}

export function inputFromHabit(habit: Habit): HabitInput {
  return {
    title: habit.title,
    cue: habit.cue,
    notes: habit.notes,
    color: habit.color,
    frequencyType: habit.schedule.frequencyType,
    days: habit.schedule.days,
    weeklyTarget: habit.schedule.weeklyTarget,
  }
}

export function insightRange(days = 365): { from: string; to: string } {
  const to = new Date()
  return { from: localDate(subDays(to, days - 1)), to: localDate(to) }
}

export function recentDayLabel(value: string): { weekday: string; day: string } {
  const date = parseISO(value)
  return { weekday: format(date, 'EEE').slice(0, 2), day: format(date, 'd') }
}

export function todayProgress(habits: TodayHabit[]): { done: number; due: number; rate: number } {
  const trackable = habits.filter(({ todayState }) => !['PAUSED', 'NOT_SCHEDULED'].includes(todayState))
  const done = trackable.filter(({ todayState }) => todayState === 'DONE').length
  return { done, due: trackable.length, rate: trackable.length === 0 ? 0 : Math.round(done * 100 / trackable.length) }
}
