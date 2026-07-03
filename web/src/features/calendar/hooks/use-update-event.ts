import { useRescheduleEvent } from "@/lib/calendar-api";

import type { IEvent } from "@/features/calendar/interfaces";

/** Drag-drop landing: persist the new span; task chips are read-only here. */
export function useUpdateEvent() {
  const reschedule = useRescheduleEvent();

  const updateEvent = (event: IEvent) => {
    if (event.kind === "task") return;
    reschedule.mutate({
      id: event.id,
      startAt: new Date(event.startDate).toISOString(),
      endAt: new Date(event.endDate).toISOString(),
    });
  };

  return { updateEvent };
}
