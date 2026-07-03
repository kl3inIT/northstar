import { z } from "zod";

const WEEKDAY_CODES = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"] as const;

/** Combine a picked day with an "HH:mm" time-input value. */
export function combine(date: Date, time: string): Date {
  const [hour = 0, minute = 0] = time.split(":").map(Number);
  const result = new Date(date);
  result.setHours(hour, minute, 0, 0);
  return result;
}

export const eventSchema = z
  .object({
    title: z.string().min(1, "Bắt buộc nhập tiêu đề"),
    description: z.string().optional(),
    allDay: z.boolean(),
    startDate: z.date({ error: "Chọn ngày bắt đầu" }),
    startTime: z.string().regex(/^\d{2}:\d{2}$/, "Chọn giờ bắt đầu"),
    endDate: z.date({ error: "Chọn ngày kết thúc" }),
    endTime: z.string().regex(/^\d{2}:\d{2}$/, "Chọn giờ kết thúc"),
    color: z.enum(["blue", "green", "red", "yellow", "purple", "orange", "gray"], {
      error: "Chọn màu",
    }),
    disciplineId: z.string().optional(),
    // Recurrence (lịch lặp): weekly defaults byDay to the start date's weekday on submit.
    repeat: z.enum(["none", "daily", "weekly"]),
    byDay: z.array(z.enum(WEEKDAY_CODES)),
    until: z.date().optional(),
  })
  .refine(data => combine(data.startDate, data.startTime) < combine(data.endDate, data.endTime), {
    message: "Kết thúc phải sau bắt đầu",
    path: ["endTime"],
  });

export type TEventFormData = z.infer<typeof eventSchema>;
