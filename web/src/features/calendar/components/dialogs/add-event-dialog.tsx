import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";

import { useDisclosure } from "@/hooks/use-disclosure";
import { useCreateEvent } from "@/lib/calendar-api";

import { Button } from "@/components/ui/button";
import { Dialog, DialogHeader, DialogClose, DialogContent, DialogTrigger, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";

import { combine, eventSchema } from "@/features/calendar/schemas";
import { EventFormFields } from "@/features/calendar/components/dialogs/event-form-fields";

import type { EventColor } from "@/lib/calendar-api";
import type { TEventFormData } from "@/features/calendar/schemas";

interface IProps {
  children: React.ReactNode;
  startDate?: Date;
  startTime?: { hour: number; minute: number };
}

const pad = (n: number) => String(n).padStart(2, "0");

export function AddEventDialog({ children, startDate, startTime }: IProps) {
  const { isOpen, onClose, onToggle } = useDisclosure();
  const createEvent = useCreateEvent();

  const form = useForm<TEventFormData>({
    resolver: zodResolver(eventSchema),
    defaultValues: {
      title: "",
      description: "",
      allDay: false,
      color: "blue",
      startDate,
      startTime: startTime ? `${pad(startTime.hour)}:${pad(startTime.minute)}` : "",
      endDate: startDate,
      endTime: startTime ? `${pad(startTime.hour + 1)}:${pad(startTime.minute)}` : "",
    },
  });

  const onSubmit = (values: TEventFormData) => {
    createEvent.mutate(
      {
        title: values.title,
        notes: values.description?.trim() ? values.description : undefined,
        startAt: combine(values.startDate, values.allDay ? "00:00" : values.startTime).toISOString(),
        endAt: combine(values.endDate, values.allDay ? "23:59" : values.endTime).toISOString(),
        allDay: values.allDay,
        color: values.color.toUpperCase() as EventColor,
      },
      {
        onSuccess: () => toast.success("Đã tạo event"),
      },
    );
    onClose();
    form.reset();
  };

  return (
    <Dialog open={isOpen} onOpenChange={onToggle}>
      <DialogTrigger asChild>{children}</DialogTrigger>

      <DialogContent>
        <DialogHeader>
          <DialogTitle>Thêm event</DialogTitle>
          <DialogDescription>Chặn thời gian cho việc diễn ra — học, lớp, hẹn.</DialogDescription>
        </DialogHeader>

        <EventFormFields form={form} onSubmit={onSubmit} />

        <DialogFooter>
          <DialogClose asChild>
            <Button type="button" variant="outline">
              Hủy
            </Button>
          </DialogClose>

          <Button form="event-form" type="submit">
            Tạo event
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
