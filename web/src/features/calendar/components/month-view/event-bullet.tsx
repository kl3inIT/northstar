import { cva } from "class-variance-authority";

import { cn } from "@/lib/utils";
import { CALENDAR_EVENT_BULLET_VARIANTS } from "@/features/calendar/calendar-color-tokens";

import type { TEventColor } from "@/features/calendar/types";

const eventBulletVariants = cva("size-2 rounded-full", {
  variants: {
    color: CALENDAR_EVENT_BULLET_VARIANTS,
  },
  defaultVariants: {
    color: "blue",
  },
});

export function EventBullet({ color, className }: { color: TEventColor; className: string }) {
  return <div className={cn(eventBulletVariants({ color, className }))} />;
}
