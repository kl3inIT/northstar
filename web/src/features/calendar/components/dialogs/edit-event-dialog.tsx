import { useEffect } from "react";
import { format, parseISO } from "date-fns";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";

import { useDisclosure } from "@/hooks/use-disclosure";
import { useEventMaster, useUpdateEventMutation } from "@/lib/calendar-api";

import { Button } from "@/components/ui/button";
import { Dialog, DialogHeader, DialogClose, DialogContent, DialogTrigger, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";

import { combine, eventSchema } from "@/features/calendar/schemas";
import { buildRrule, parseRrule } from "@/features/calendar/recurrence";
import { EventFormFields } from "@/features/calendar/components/dialogs/event-form-fields";

import type { EventColor } from "@/lib/calendar-api";
import type { IEvent } from "@/features/calendar/interfaces";
import type { TEventFormData } from "@/features/calendar/schemas";

interface IProps {
  children: React.ReactNode;
  event: IEvent;
}

/**
 * Edits the event ROW — for a recurring series that is the whole chuỗi, so the
 * form prefills from the master (fetched on open), not from the clicked buổi.
 */
export function EditEventDialog({ children, event }: IProps) {
  const { isOpen, onClose, onToggle } = useDisclosure();
  const updateEvent = useUpdateEventMutation();
  const isRecurring = !!event.rrule;
  const { data: master } = useEventMaster(isOpen && isRecurring ? (event.masterId ?? event.id) : undefined);

  const form = useForm<TEventFormData>({
    resolver: zodResolver(eventSchema),
    defaultValues: {
      title: event.title,
      description: event.description,
      allDay: event.allDay ?? false,
      startDate: parseISO(event.startDate),
      startTime: format(parseISO(event.startDate), "HH:mm"),
      endDate: parseISO(event.endDate),
      endTime: format(parseISO(event.endDate), "HH:mm"),
      color: event.color,
      disciplineId: event.disciplineId,
      ...parseRrule(event.rrule),
    },
  });

  // Recurring: swap the occurrence prefill for the series anchor once it loads,
  // otherwise saving would silently re-anchor the chuỗi at the clicked buổi.
  useEffect(() => {
    if (!master) return;
    form.reset({
      title: master.title,
      description: master.notes ?? "",
      allDay: master.allDay ?? false,
      startDate: parseISO(master.startAt),
      startTime: format(parseISO(master.startAt), "HH:mm"),
      endDate: parseISO(master.endAt),
      endTime: format(parseISO(master.endAt), "HH:mm"),
      color: master.color.toLowerCase() as TEventFormData["color"],
      disciplineId: master.disciplineId,
      ...parseRrule(master.rrule ?? undefined),
    });
  }, [master, form]);

  const onSubmit = (values: TEventFormData) => {
    updateEvent.mutate(
      {
        id: event.masterId ?? event.id,
        body: {
          title: values.title,
          notes: values.description?.trim() ? values.description : undefined,
          startAt: combine(values.startDate, values.allDay ? "00:00" : values.startTime).toISOString(),
          endAt: combine(values.endDate, values.allDay ? "23:59" : values.endTime).toISOString(),
          allDay: values.allDay,
          color: values.color.toUpperCase() as EventColor,
          disciplineId: values.disciplineId,
          rrule: buildRrule(values, values.startDate),
        },
      },
      {
        onSuccess: () => toast.success(isRecurring ? "Saved the whole series" : "Event saved"),
      },
    );
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={onToggle}>
      <DialogTrigger asChild>{children}</DialogTrigger>

      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isRecurring ? "Edit recurring series" : "Edit event"}</DialogTitle>
          {isRecurring && (
            <DialogDescription>Changes apply to the WHOLE SERIES; the start date anchors the series.</DialogDescription>
          )}
        </DialogHeader>

        <EventFormFields form={form} onSubmit={onSubmit} />

        <DialogFooter>
          <DialogClose asChild>
            <Button type="button" variant="outline">
              Cancel
            </Button>
          </DialogClose>

          <Button form="event-form" type="submit">
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
