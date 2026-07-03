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
  kind: "event" | "task";
  taskDone?: boolean;
}

export interface ICalendarCell {
  day: number;
  currentMonth: boolean;
  date: Date;
}
