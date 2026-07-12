import type { TEventColor } from '@/features/calendar/types'

/** Stable user-selectable data colors. These are category tokens, not UI intent states. */
export const CALENDAR_EVENT_COLOR_VARIANTS = {
  blue: 'border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-300 [&_.event-dot]:fill-blue-600',
  green: 'border-green-200 bg-green-50 text-green-700 dark:border-green-800 dark:bg-green-950 dark:text-green-300 [&_.event-dot]:fill-green-600',
  red: 'border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300 [&_.event-dot]:fill-red-600',
  yellow: 'border-yellow-200 bg-yellow-50 text-yellow-700 dark:border-yellow-800 dark:bg-yellow-950 dark:text-yellow-300 [&_.event-dot]:fill-yellow-600',
  purple: 'border-purple-200 bg-purple-50 text-purple-700 dark:border-purple-800 dark:bg-purple-950 dark:text-purple-300 [&_.event-dot]:fill-purple-600',
  orange: 'border-orange-200 bg-orange-50 text-orange-700 dark:border-orange-800 dark:bg-orange-950 dark:text-orange-300 [&_.event-dot]:fill-orange-600',
  gray: 'border-neutral-200 bg-neutral-50 text-neutral-900 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300 [&_.event-dot]:fill-neutral-600',
  'blue-dot': 'bg-neutral-50 dark:bg-neutral-900 [&_.event-dot]:fill-blue-600',
  'green-dot': 'bg-neutral-50 dark:bg-neutral-900 [&_.event-dot]:fill-green-600',
  'red-dot': 'bg-neutral-50 dark:bg-neutral-900 [&_.event-dot]:fill-red-600',
  'yellow-dot': 'bg-neutral-50 dark:bg-neutral-900 [&_.event-dot]:fill-yellow-600',
  'purple-dot': 'bg-neutral-50 dark:bg-neutral-900 [&_.event-dot]:fill-purple-600',
  'orange-dot': 'bg-neutral-50 dark:bg-neutral-900 [&_.event-dot]:fill-orange-600',
  'gray-dot': 'bg-neutral-50 dark:bg-neutral-900 [&_.event-dot]:fill-neutral-600',
} as const

export const CALENDAR_WEEK_EVENT_COLOR_VARIANTS = {
  ...CALENDAR_EVENT_COLOR_VARIANTS,
  gray: 'border-neutral-200 bg-neutral-50 text-neutral-700 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-300 [&_.event-dot]:fill-neutral-600',
} as const

export const CALENDAR_EVENT_BULLET_VARIANTS: Record<TEventColor, string> = {
  blue: 'bg-blue-600 dark:bg-blue-500',
  green: 'bg-green-600 dark:bg-green-500',
  red: 'bg-red-600 dark:bg-red-500',
  yellow: 'bg-yellow-600 dark:bg-yellow-500',
  purple: 'bg-purple-600 dark:bg-purple-500',
  orange: 'bg-orange-600 dark:bg-orange-500',
  gray: 'bg-neutral-600 dark:bg-neutral-500',
}

export const CALENDAR_EVENT_SOLID_VARIANTS: Record<TEventColor, string> = {
  blue: 'bg-blue-600',
  green: 'bg-green-600',
  red: 'bg-red-600',
  yellow: 'bg-yellow-600',
  purple: 'bg-purple-600',
  orange: 'bg-orange-600',
  gray: 'bg-neutral-600',
}
