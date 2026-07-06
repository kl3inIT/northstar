import { useEffect, useState } from "react";
import { format, parseISO } from "date-fns";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";

import { useDisclosure } from "@/hooks/use-disclosure";
import { useDetachOccurrence, useEventMaster, useUpdateEventMutation } from "@/lib/calendar-api";

import { Button } from "@/components/ui/button";
import { Dialog, DialogHeader, DialogClose, DialogContent, DialogTrigger, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

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

type EditScope = "occurrence" | "series";

/**
 * Edits an event. For a recurring series the dialog asks WHICH scope first —
 * "chỉ buổi này" detaches the clicked occurrence into a standalone event
 * (same mechanism as dragging one buổi), "cả chuỗi" edits the master row and
 * prefills from the series anchor, not the clicked buổi.
 */
export function EditEventDialog({ children, event }: IProps) {
  const { isOpen, onClose, onToggle } = useDisclosure();
  const updateEvent = useUpdateEventMutation();
  const detachOccurrence = useDetachOccurrence();
  const isRecurring = !!event.rrule;
  const [scope, setScope] = useState<EditScope>("occurrence");
  const editingSeries = isRecurring && scope === "series";
  const { data: master } = useEventMaster(isOpen && editingSeries ? (event.masterId ?? event.id) : undefined);

  const occurrenceValues = (): TEventFormData => ({
    title: event.title,
    description: event.description,
    allDay: event.allDay ?? false,
    startDate: parseISO(event.startDate),
    startTime: format(parseISO(event.startDate), "HH:mm"),
    endDate: parseISO(event.endDate),
    endTime: format(parseISO(event.endDate), "HH:mm"),
    color: event.color,
    disciplineId: event.disciplineId ?? undefined,
    // A detached buổi is one-off — recurrence stays with the series.
    repeat: "none",
    byDay: [],
    until: undefined,
  });

  const form = useForm<TEventFormData>({
    resolver: zodResolver(eventSchema),
    defaultValues: isRecurring
      ? occurrenceValues()
      : { ...occurrenceValues(), ...parseRrule(event.rrule) },
  });

  // Series scope prefills from the master once it loads (saving from the
  // occurrence's values would silently re-anchor the chuỗi at the clicked buổi);
  // switching back to occurrence scope restores the clicked buổi's values.
  useEffect(() => {
    if (!isRecurring) return;
    if (scope === "occurrence") {
      form.reset(occurrenceValues());
    } else if (master) {
      form.reset({
        title: master.title,
        description: master.notes ?? "",
        allDay: master.allDay ?? false,
        startDate: parseISO(master.startAt),
        startTime: format(parseISO(master.startAt), "HH:mm"),
        endDate: parseISO(master.endAt),
        endTime: format(parseISO(master.endAt), "HH:mm"),
        color: master.color.toLowerCase() as TEventFormData["color"],
        disciplineId: master.disciplineId ?? undefined,
        ...parseRrule(master.rrule ?? undefined),
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [master, scope, isRecurring]);

  const onSubmit = (values: TEventFormData) => {
    const body = {
      title: values.title,
      notes: values.description?.trim() ? values.description : undefined,
      startAt: combine(values.startDate, values.allDay ? "00:00" : values.startTime).toISOString(),
      endAt: combine(values.endDate, values.allDay ? "23:59" : values.endTime).toISOString(),
      allDay: values.allDay,
      color: values.color.toUpperCase() as EventColor,
      disciplineId: values.disciplineId,
    };
    if (isRecurring && scope === "occurrence") {
      detachOccurrence.mutate(
        {
          masterId: event.masterId ?? event.id,
          occurrenceStart: event.occurrenceStart ?? event.startDate,
          // Prefilled repeat is "none" (a detached buổi is a one-off), but the
          // form stays honest: setting a repeat here starts a new series.
          body: { ...body, rrule: buildRrule(values, values.startDate) },
        },
        {
          onSuccess: () => toast.success("Saved this occurrence only"),
          onError: () => toast.error("Saving failed — try again."),
        },
      );
    } else {
      updateEvent.mutate(
        {
          id: event.masterId ?? event.id,
          body: { ...body, rrule: buildRrule(values, values.startDate) },
        },
        {
          onSuccess: () => toast.success(isRecurring ? "Saved the whole series" : "Event saved"),
          onError: () => toast.error("Saving failed — try again."),
        },
      );
    }
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={onToggle}>
      <DialogTrigger asChild>{children}</DialogTrigger>

      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isRecurring ? "Edit recurring event" : "Edit event"}</DialogTitle>
          {isRecurring && (
            <DialogDescription>
              {scope === "occurrence"
                ? "Only this buổi changes — it detaches from the series."
                : "Changes apply to the WHOLE SERIES; the start date anchors the series."}
            </DialogDescription>
          )}
        </DialogHeader>

        {isRecurring && (
          <ToggleGroup
            type="single"
            variant="outline"
            value={scope}
            onValueChange={(v) => v && setScope(v as EditScope)}
            className="w-full"
          >
            <ToggleGroupItem value="occurrence" className="flex-1">
              This occurrence
            </ToggleGroupItem>
            <ToggleGroupItem value="series" className="flex-1">
              Whole series
            </ToggleGroupItem>
          </ToggleGroup>
        )}

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
