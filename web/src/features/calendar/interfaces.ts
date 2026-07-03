import type { TEventColor } from "@/features/calendar/types";

/**
 * The UI model every calendar view renders. Events come from
 * /api/calendar/events; dated tasks are overlaid as read-only all-day items
 * (kind "task") — they cannot be dragged or edited here, only toggled done.
 */
export interface IEvent {
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
  /** Original task row (kind "task") — drag needs its fields to PUT the new due date. */
  task?: { id: string; title: string; notes: string | null; dueTime: string | null };
}

export interface ICalendarCell {
  day: number;
  currentMonth: boolean;
  date: Date;
}
