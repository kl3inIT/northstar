import type { TEventColor } from "@/features/calendar/types";

/**
 * The UI model every calendar view renders. Events come from
 * /api/calendar/events; dated tasks are overlaid as read-only all-day items
 * (kind "task") — they cannot be dragged or edited here, only toggled done.
 */
export interface IEvent {
  /**
   * Unique per RENDERED item: the server id, or `${serverId}@${startAt}` for
   * one occurrence of a recurring series (occurrences share a master row).
   * React keys and month-view position maps rely on this; API calls must use
   * {@link masterId} ?? id.
   */
  id: string;
  startDate: string;
  endDate: string;
  title: string;
  color: TEventColor;
  description: string;
  allDay?: boolean;
  disciplineId?: string;
  disciplineName?: string;
  kind: "event" | "task";
  taskDone?: boolean;
  /** RFC 5545 rule — present marks this as one buổi of a recurring series. */
  rrule?: string;
  /** Server row id of the recurring master (recurring occurrences only). */
  masterId?: string;
  /** Server-issued start of this occurrence — the "chỉ buổi này" key; survives drag. */
  occurrenceStart?: string;
  /** Original task row (kind "task") — drag needs its fields to PUT the new due date. */
  task?: {
    id: string;
    title: string;
    notes: string | null;
    dueTime: string | null;
    plannedDate: string | null;
    disciplineId: string | null;
  };
}

export interface ICalendarCell {
  day: number;
  currentMonth: boolean;
  date: Date;
}
