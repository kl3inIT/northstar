import assert from 'node:assert/strict'
import test from 'node:test'
import {
  defaultHabitInput,
  scheduleLabel,
  todayProgress,
} from '../src/features/habits/habit-utils.ts'

test('new habits default to a daily behaviour schedule', () => {
  const input = defaultHabitInput()
  assert.equal(input.frequencyType, 'ON_DAYS')
  assert.equal(input.days.length, 7)
  assert.equal(input.color, 'GREEN')
})

test('schedule labels distinguish selected days from flexible weekly targets', () => {
  const base = {
    id: 'habit', title: 'Read', color: 'GREEN', status: 'ACTIVE', timezone: 'Asia/Bangkok',
    paused: false, createdAt: '2026-07-13T00:00:00Z', version: 0,
  }
  assert.equal(scheduleLabel({ ...base, schedule: {
    frequencyType: 'ON_DAYS', days: ['MONDAY', 'WEDNESDAY', 'FRIDAY'], weeklyTarget: 1, effectiveFrom: '2026-07-13',
  } }), 'mon, wed, fri')
  assert.equal(scheduleLabel({ ...base, schedule: {
    frequencyType: 'WEEKLY_TARGET', days: [], weeklyTarget: 3, effectiveFrom: '2026-07-13',
  } }), '3 times a week')
})

test('today progress excludes paused and unscheduled habits from the denominator', () => {
  const rows = [
    { todayState: 'DONE' },
    { todayState: 'OPEN' },
    { todayState: 'PAUSED' },
    { todayState: 'NOT_SCHEDULED' },
  ]
  assert.deepEqual(todayProgress(rows), { done: 1, due: 2, rate: 50 })
})
