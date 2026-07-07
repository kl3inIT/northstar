import type { Discipline } from './disciplines-api'

/**
 * The one mapping from a discipline's ColorName to CSS. Was triplicated across
 * tasks / disciplines (Tailwind class) and projects (hex); keep it here so a
 * new palette entry is added once.
 */

/** ColorName → Tailwind background class for a status dot. */
export const DISCIPLINE_DOT: Record<Discipline['color'], string> = {
  BLUE: 'bg-blue-600',
  GREEN: 'bg-green-600',
  RED: 'bg-red-600',
  YELLOW: 'bg-yellow-600',
  PURPLE: 'bg-purple-600',
  ORANGE: 'bg-orange-600',
  GRAY: 'bg-neutral-600',
}

/** ColorName → a real CSS hex, for canvas/SVG contexts (Gantt) that can't use a class. */
export const DISCIPLINE_HEX: Record<Discipline['color'], string> = {
  BLUE: '#2563eb',
  GREEN: '#16a34a',
  RED: '#dc2626',
  YELLOW: '#ca8a04',
  PURPLE: '#9333ea',
  ORANGE: '#ea580c',
  GRAY: '#525252',
}

/** The ColorName options, in palette order. */
export const DISCIPLINE_COLORS = Object.keys(DISCIPLINE_DOT) as Discipline['color'][]
