import { format } from 'date-fns'

/**
 * A Date as a local-calendar `yyyy-MM-dd` string — the wire format the task and
 * calendar APIs speak. One home for what was hand-rolled in several pages;
 * date-fns keeps it local-zone correct.
 */
export function iso(d: Date): string {
  return format(d, 'yyyy-MM-dd')
}
