import { format, parseISO } from "date-fns";
import { Calendar, CheckCircle2, Clock, Tag, Text, Trash2, Undo2 } from "lucide-react";
import { toast } from "sonner";

import { useDeleteEvent } from "@/lib/calendar-api";
import { useSetTaskDone } from "@/lib/tasks-api";
import { useDisclosure } from "@/hooks/use-disclosure";

import { Button } from "@/components/ui/button";
import { EditEventDialog } from "@/features/calendar/components/dialogs/edit-event-dialog";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";

import type { IEvent } from "@/features/calendar/interfaces";

interface IProps {
  event: IEvent;
  children: React.ReactNode;
}

export function EventDetailsDialog({ event, children }: IProps) {
  const { isOpen, onClose, onToggle } = useDisclosure();
  const deleteEvent = useDeleteEvent();
  const setTaskDone = useSetTaskDone();

  const startDate = parseISO(event.startDate);
  const endDate = parseISO(event.endDate);
  const isTask = event.kind === "task";
  const timeFormat = event.allDay || isTask ? "MMM d, yyyy" : "MMM d, yyyy h:mm a";

  const handleDelete = () => {
    deleteEvent.mutate(event.id, { onSuccess: () => toast.success("Đã xóa event") });
    onClose();
  };

  const handleToggleTask = () => {
    setTaskDone.mutate({ id: event.id, done: !event.taskDone });
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={onToggle}>
      <DialogTrigger asChild>{children}</DialogTrigger>

      <DialogContent>
        <DialogHeader>
          <DialogTitle>{event.title}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="flex items-start gap-2">
            <Calendar className="mt-1 size-4 shrink-0" />
            <div>
              <p className="text-sm font-medium">{isTask ? "Đến hạn" : "Bắt đầu"}</p>
              <p className="text-sm text-muted-foreground">{format(startDate, timeFormat)}</p>
            </div>
          </div>

          {!isTask && (
            <div className="flex items-start gap-2">
              <Clock className="mt-1 size-4 shrink-0" />
              <div>
                <p className="text-sm font-medium">Kết thúc</p>
                <p className="text-sm text-muted-foreground">{format(endDate, timeFormat)}</p>
              </div>
            </div>
          )}

          {event.disciplineName && (
            <div className="flex items-start gap-2">
              <Tag className="mt-1 size-4 shrink-0" />
              <div>
                <p className="text-sm font-medium">Discipline</p>
                <p className="text-sm text-muted-foreground">{event.disciplineName}</p>
              </div>
            </div>
          )}

          {event.description && (
            <div className="flex items-start gap-2">
              <Text className="mt-1 size-4 shrink-0" />
              <div>
                <p className="text-sm font-medium">Ghi chú</p>
                <p className="text-sm text-muted-foreground">{event.description}</p>
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          {isTask ? (
            <Button type="button" onClick={handleToggleTask}>
              {event.taskDone ? (
                <>
                  <Undo2 /> Mở lại
                </>
              ) : (
                <>
                  <CheckCircle2 /> Đánh dấu xong
                </>
              )}
            </Button>
          ) : (
            <>
              <Button type="button" variant="outline" onClick={handleDelete}>
                <Trash2 /> Xóa
              </Button>
              <EditEventDialog event={event}>
                <Button type="button" variant="outline">
                  Sửa
                </Button>
              </EditEventDialog>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
