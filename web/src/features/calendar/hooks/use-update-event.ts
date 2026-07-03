import { format, parseISO } from "date-fns";

import { useDetachOccurrence, useRescheduleEvent } from "@/lib/calendar-api";
import { useUpdateTask } from "@/lib/tasks-api";

import type { EventColor } from "@/lib/calendar-api";
import type { IEvent } from "@/features/calendar/interfaces";

/**
 * Drag-drop landing. An event moves its time span; a task chip moves its
 * DEADLINE — only dueDate changes, dueTime and the text fields stay. Dragging
 * one buổi of a recurring series moves ONLY that occurrence (GCal "chỉ buổi
 * này"): the occurrence is cancelled and re-created as a standalone event.
 */
export function useUpdateEvent() {
  const reschedule = useRescheduleEvent();
  const updateTask = useUpdateTask();
  const detach = useDetachOccurrence();

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
          disciplineId: event.task.disciplineId ?? undefined,
        },
      });
      return;
    }
    if (event.rrule && event.masterId && event.occurrenceStart) {
      detach.mutate({
        masterId: event.masterId,
        occurrenceStart: event.occurrenceStart,
        body: {
          title: event.title,
          notes: event.description?.trim() ? event.description : undefined,
          startAt: new Date(event.startDate).toISOString(),
          endAt: new Date(event.endDate).toISOString(),
          allDay: event.allDay ?? false,
          color: event.color.toUpperCase() as EventColor,
          disciplineId: event.disciplineId,
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
