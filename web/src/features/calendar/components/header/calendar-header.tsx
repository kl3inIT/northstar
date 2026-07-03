import { CalendarRange, Columns, Grid2x2, Grid3x3, List, Plus } from "lucide-react";

import { Button } from "@/components/ui/button";

import { useCalendar } from "@/features/calendar/contexts/calendar-context";
import { TodayButton } from "@/features/calendar/components/header/today-button";
import { DateNavigator } from "@/features/calendar/components/header/date-navigator";
import { AddEventDialog } from "@/features/calendar/components/dialogs/add-event-dialog";

import { cn } from "@/lib/utils";

import type { LucideIcon } from "lucide-react";
import type { IEvent } from "@/features/calendar/interfaces";
import type { TCalendarView } from "@/features/calendar/types";

interface IProps {
  events: IEvent[];
}

const VIEWS: { view: TCalendarView; label: string; icon: LucideIcon }[] = [
  { view: "day", label: "day", icon: List },
  { view: "week", label: "week", icon: Columns },
  { view: "month", label: "month", icon: Grid2x2 },
  { view: "year", label: "year", icon: Grid3x3 },
  { view: "agenda", label: "agenda", icon: CalendarRange },
];

export function CalendarHeader({ events }: IProps) {
  const { view, setView } = useCalendar();

  return (
    <div className="flex flex-col gap-4 border-b p-4 lg:flex-row lg:items-center lg:justify-between">
      <div className="flex items-center gap-3">
        <TodayButton />
        <DateNavigator view={view} events={events} />
      </div>

      <div className="flex flex-col items-center gap-1.5 sm:flex-row sm:justify-between">
        <div className="inline-flex">
          {VIEWS.map(({ view: v, label, icon: Icon }, i) => (
            <Button
              key={v}
              aria-label={`View by ${label}`}
              size="icon"
              variant={view === v ? "default" : "outline"}
              className={cn(
                "[&_svg]:size-5",
                i === 0 && "rounded-r-none",
                i > 0 && i < VIEWS.length - 1 && "-ml-px rounded-none",
                i === VIEWS.length - 1 && "-ml-px rounded-l-none",
              )}
              onClick={() => setView(v)}
            >
              <Icon strokeWidth={1.8} />
            </Button>
          ))}
        </div>

        <AddEventDialog>
          <Button className="w-full sm:w-auto">
            <Plus />
            Thêm event
          </Button>
        </AddEventDialog>
      </div>
    </div>
  );
}
