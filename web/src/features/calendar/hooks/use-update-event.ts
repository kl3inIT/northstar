import { format, parseISO } from "date-fns";

import { useRescheduleEvent } from "@/lib/calendar-api";
import { useUpdateTask } from "@/lib/tasks-api";

import type { IEvent } from "@/features/calendar/interfaces";

/**
 * Drag-drop landing. An event moves its time span; a task chip moves its
 * DEADLINE — only dueDate changes, dueTime and the text fields stay.
 */
export function useUpdateEvent() {
  const reschedule = useRescheduleEvent();
  const updateTask = useUpdateTask();

  const updateEvent = (event: IEvent) => {
    if (event.kind === "task") {
      if (!event.task) return;
      updateTask.mutate({
        id: event.task.id,
        body: {
          title: event.task.title,
          notes: event.task.notes ?? undefined,
          dueDate: format(parseISO(event.startDate), "yyyy-MM-dd"),
          dueTime: event.task.dueTime ?? undefined,
        },
      });
      return;
    }
    reschedule.mutate({
      id: event.id,
      startAt: new Date(event.startDate).toISOString(),
      endAt: new Date(event.endDate).toISOString(),
    });
  };

  return { updateEvent };
}
