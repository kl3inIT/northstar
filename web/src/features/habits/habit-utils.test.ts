import { describe, expect, test } from 'vitest'
import type { Habit, TodayHabit } from '@/lib/habits-api'
import {
  defaultHabitInput,
  scheduleLabel,
  todayProgress,
} from './habit-utils'

const baseHabit = {
  id: 'habit',
  title: 'Read',
  color: 'GREEN',
  status: 'ACTIVE',
  timezone: 'Asia/Bangkok',
  paused: false,
  createdAt: '2026-07-13T00:00:00Z',
  version: 0,
} as const

describe('habit view logic', () => {
  test('new habits default to a daily behaviour schedule', () => {
    const input = defaultHabitInput()

    expect(input.frequencyType).toBe('ON_DAYS')
    expect(input.days).toHaveLength(7)
    expect(input.color).toBe('GREEN')
  })

  test('schedule labels distinguish selected days from flexible weekly targets', () => {
    const selectedDays = {
      ...baseHabit,
      schedule: {
        frequencyType: 'ON_DAYS',
        days: ['MONDAY', 'WEDNESDAY', 'FRIDAY'],
        weeklyTarget: 1,
        effectiveFrom: '2026-07-13',
      },
    } as Habit
    const weeklyTarget = {
      ...baseHabit,
      schedule: {
        frequencyType: 'WEEKLY_TARGET',
        days: [],
        weeklyTarget: 3,
        effectiveFrom: '2026-07-13',
      },
    } as Habit

    expect(scheduleLabel(selectedDays)).toBe('mon, wed, fri')
    expect(scheduleLabel(weeklyTarget)).toBe('3 times a week')
  })

  test('today progress excludes paused and unscheduled habits from the denominator', () => {
    const rows = [
      { todayState: 'DONE' },
      { todayState: 'OPEN' },
      { todayState: 'PAUSED' },
      { todayState: 'NOT_SCHEDULED' },
    ] as TodayHabit[]

    expect(todayProgress(rows)).toEqual({ done: 1, due: 2, rate: 50 })
  })
})
