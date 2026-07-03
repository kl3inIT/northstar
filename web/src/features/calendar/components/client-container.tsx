import { useMemo } from "react";
import { isSameDay, parseISO } from "date-fns";

import { useCalendar } from "@/features/calendar/contexts/calendar-context";

import { DndProviderWrapper } from "@/features/calendar/components/dnd/dnd-provider";

import { CalendarHeader } from "@/features/calendar/components/header/calendar-header";
import { CalendarYearView } from "@/features/calendar/components/year-view/calendar-year-view";
import { CalendarMonthView } from "@/features/calendar/components/month-view/calendar-month-view";
import { CalendarAgendaView } from "@/features/calendar/components/agenda-view/calendar-agenda-view";
import { CalendarDayView } from "@/features/calendar/components/week-and-day-view/calendar-day-view";
import { CalendarWeekView } from "@/features/calendar/components/week-and-day-view/calendar-week-view";

export function ClientContainer() {
  const { selectedDate, view, events } = useCalendar();

  const filteredEvents = useMemo(() => {
    return events.filter(event => {
      const eventStartDate = parseISO(event.startDate);
      const eventEndDate = parseISO(event.endDate);

      if (view === "year") {
        const yearStart = new Date(selectedDate.getFullYear(), 0, 1);
        const yearEnd = new Date(selectedDate.getFullYear(), 11, 31, 23, 59, 59, 999);
        return eventStartDate <= yearEnd && eventEndDate >= yearStart;
      }

      if (view === "month" || view === "agenda") {
        const monthStart = new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1);
        const monthEnd = new Date(selectedDate.getFullYear(), selectedDate.getMonth() + 1, 0, 23, 59, 59, 999);
        return eventStartDate <= monthEnd && eventEndDate >= monthStart;
      }

      if (view === "week") {
        const dayOfWeek = (selectedDate.getDay() + 6) % 7; // Monday-first

        const weekStart = new Date(selectedDate);
        weekStart.setDate(selectedDate.getDate() - dayOfWeek);
        weekStart.setHours(0, 0, 0, 0);

        const weekEnd = new Date(weekStart);
        weekEnd.setDate(weekStart.getDate() + 6);
        weekEnd.setHours(23, 59, 59, 999);

        return eventStartDate <= weekEnd && eventEndDate >= weekStart;
      }

      if (view === "day") {
        const dayStart = new Date(selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(), 0, 0, 0);
        const dayEnd = new Date(selectedDate.getFullYear(), selectedDate.getMonth(), selectedDate.getDate(), 23, 59, 59);
        return eventStartDate <= dayEnd && eventEndDate >= dayStart;
      }

      return false;
    });
  }, [selectedDate, events, view]);

  // All-day items (incl. task chips) join the multi-day row instead of
  // rendering as a 24h block on the time grid.
  const singleDayEvents = filteredEvents.filter(event => {
    if (event.allDay || event.kind === "task") return false;
    const startDate = parseISO(event.startDate);
    const endDate = parseISO(event.endDate);
    return isSameDay(startDate, endDate);
  });

  const multiDayEvents = filteredEvents.filter(event => {
    if (event.allDay || event.kind === "task") return true;
    const startDate = parseISO(event.startDate);
    const endDate = parseISO(event.endDate);
    return !isSameDay(startDate, endDate);
  });

  // For year view, we only care about the start date
  // by using the same date for both start and end,
  // we ensure only the start day will show a dot
  const eventStartDates = useMemo(() => {
    return filteredEvents.map(event => ({ ...event, endDate: event.startDate }));
  }, [filteredEvents]);

  return (
    <div className="flex h-full flex-col overflow-hidden rounded-xl border">
      <CalendarHeader events={filteredEvents} />

      <DndProviderWrapper>
        {view === "day" && <CalendarDayView singleDayEvents={singleDayEvents} multiDayEvents={multiDayEvents} />}
        {view === "month" && <CalendarMonthView singleDayEvents={singleDayEvents} multiDayEvents={multiDayEvents} />}
        {view === "week" && <CalendarWeekView singleDayEvents={singleDayEvents} multiDayEvents={multiDayEvents} />}
        {view === "year" && <CalendarYearView allEvents={eventStartDates} />}
        {view === "agenda" && <CalendarAgendaView singleDayEvents={singleDayEvents} multiDayEvents={multiDayEvents} />}
      </DndProviderWrapper>
    </div>
  );
}
