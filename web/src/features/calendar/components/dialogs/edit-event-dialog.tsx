import { format, parseISO } from "date-fns";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";

import { useDisclosure } from "@/hooks/use-disclosure";
import { useUpdateEventMutation } from "@/lib/calendar-api";

import { Button } from "@/components/ui/button";
import { Dialog, DialogHeader, DialogClose, DialogContent, DialogTrigger, DialogTitle, DialogFooter } from "@/components/ui/dialog";

import { combine, eventSchema } from "@/features/calendar/schemas";
import { EventFormFields } from "@/features/calendar/components/dialogs/event-form-fields";

import type { EventColor } from "@/lib/calendar-api";
import type { IEvent } from "@/features/calendar/interfaces";
import type { TEventFormData } from "@/features/calendar/schemas";

interface IProps {
  children: React.ReactNode;
  event: IEvent;
}

export function EditEventDialog({ children, event }: IProps) {
  const { isOpen, onClose, onToggle } = useDisclosure();
  const updateEvent = useUpdateEventMutation();

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
    },
  });

  const onSubmit = (values: TEventFormData) => {
    updateEvent.mutate(
      {
        id: event.id,
        body: {
          title: values.title,
          notes: values.description?.trim() ? values.description : undefined,
          startAt: combine(values.startDate, values.allDay ? "00:00" : values.startTime).toISOString(),
          endAt: combine(values.endDate, values.allDay ? "23:59" : values.endTime).toISOString(),
          allDay: values.allDay,
          color: values.color.toUpperCase() as EventColor,
          disciplineId: values.disciplineId,
        },
      },
      {
        onSuccess: () => toast.success("Đã lưu event"),
      },
    );
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={onToggle}>
      <DialogTrigger asChild>{children}</DialogTrigger>

      <DialogContent>
        <DialogHeader>
          <DialogTitle>Sửa event</DialogTitle>
        </DialogHeader>

        <EventFormFields form={form} onSubmit={onSubmit} />

        <DialogFooter>
          <DialogClose asChild>
            <Button type="button" variant="outline">
              Hủy
            </Button>
          </DialogClose>

          <Button form="event-form" type="submit">
            Lưu
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
