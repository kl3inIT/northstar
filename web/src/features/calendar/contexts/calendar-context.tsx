import { createContext, useContext, useState } from "react";

import type { Dispatch, SetStateAction } from "react";
import type { IEvent } from "@/features/calendar/interfaces";
import type { TBadgeVariant, TCalendarView, TVisibleHours, TWorkingHours } from "@/features/calendar/types";

interface ICalendarContext {
  selectedDate: Date;
  setSelectedDate: (date: Date | undefined) => void;
  view: TCalendarView;
  setView: (view: TCalendarView) => void;
  badgeVariant: TBadgeVariant;
  workingHours: TWorkingHours;
  setWorkingHours: Dispatch<SetStateAction<TWorkingHours>>;
  visibleHours: TVisibleHours;
  setVisibleHours: Dispatch<SetStateAction<TVisibleHours>>;
  events: IEvent[];
}

const CalendarContext = createContext({} as ICalendarContext);

const WORKING_HOURS: TWorkingHours = {
  0: { from: 0, to: 0 },
  1: { from: 8, to: 17 },
  2: { from: 8, to: 17 },
  3: { from: 8, to: 17 },
  4: { from: 8, to: 17 },
  5: { from: 8, to: 17 },
  6: { from: 8, to: 12 },
};

const VISIBLE_HOURS: TVisibleHours = { from: 7, to: 22 };

interface IProps {
  children: React.ReactNode;
  /** Merged events + task overlay for the visible window — fetched by the page. */
  events: IEvent[];
  selectedDate: Date;
  onSelectedDateChange: (date: Date) => void;
  view: TCalendarView;
  onViewChange: (view: TCalendarView) => void;
}

/**
 * Pure pass-through provider: the page owns view/date state (they drive the
 * range query) and the fetched events; this context fans them out to the deep
 * view/dnd components, plus local display settings (visible/working hours).
 */
export function CalendarProvider({ children, events, selectedDate, onSelectedDateChange, view, onViewChange }: IProps) {
  const [visibleHours, setVisibleHours] = useState<TVisibleHours>(VISIBLE_HOURS);
  const [workingHours, setWorkingHours] = useState<TWorkingHours>(WORKING_HOURS);

  const handleSelectDate = (date: Date | undefined) => {
    if (!date) return;
    onSelectedDateChange(date);
  };

  return (
    <CalendarContext.Provider
      value={{
        selectedDate,
        setSelectedDate: handleSelectDate,
        view,
        setView: onViewChange,
        badgeVariant: "colored",
        visibleHours,
        setVisibleHours,
        workingHours,
        setWorkingHours,
        events,
      }}
    >
      {children}
    </CalendarContext.Provider>
  );
}

export function useCalendar(): ICalendarContext {
  const context = useContext(CalendarContext);
  if (!context) throw new Error("useCalendar must be used within a CalendarProvider.");
  return context;
}
