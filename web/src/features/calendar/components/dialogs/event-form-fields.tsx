import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { SingleDayPicker } from "@/components/ui/single-day-picker";
import { Form, FormField, FormLabel, FormItem, FormControl, FormMessage } from "@/components/ui/form";
import { Select, SelectItem, SelectContent, SelectTrigger, SelectValue } from "@/components/ui/select";

import { cn } from "@/lib/utils";
import { useDisciplines } from "@/lib/disciplines-api";
import { WEEKDAYS, weekdayCodeOf } from "@/features/calendar/recurrence";

import type { UseFormReturn } from "react-hook-form";
import type { TEventFormData } from "@/features/calendar/schemas";

const NO_DISCIPLINE = "__none__";

const COLORS = [
  { value: "blue", label: "Blue", dot: "bg-blue-600" },
  { value: "green", label: "Green", dot: "bg-green-600" },
  { value: "red", label: "Red", dot: "bg-red-600" },
  { value: "yellow", label: "Yellow", dot: "bg-yellow-600" },
  { value: "purple", label: "Purple", dot: "bg-purple-600" },
  { value: "orange", label: "Orange", dot: "bg-orange-600" },
  { value: "gray", label: "Gray", dot: "bg-neutral-600" },
] as const;

interface IProps {
  form: UseFormReturn<TEventFormData>;
  onSubmit: (values: TEventFormData) => void;
}

/** The shared add/edit event form body — one source for both dialogs. */
export function EventFormFields({ form, onSubmit }: IProps) {
  const allDay = form.watch("allDay");
  const repeat = form.watch("repeat");
  const { data: disciplines = [] } = useDisciplines();

  return (
    <Form {...form}>
      <form id="event-form" onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4 py-4">
        <FormField
          control={form.control}
          name="title"
          render={({ field, fieldState }) => (
            <FormItem>
              <FormLabel htmlFor="title">Tiêu đề</FormLabel>
              <FormControl>
                <Input id="title" placeholder="Nhập tiêu đề" data-invalid={fieldState.invalid} {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="allDay"
          render={({ field }) => (
            <FormItem className="flex flex-row items-center gap-2">
              <FormControl>
                <Switch
                  checked={field.value}
                  onCheckedChange={checked => {
                    field.onChange(checked);
                    if (checked) {
                      form.setValue("startTime", "00:00");
                      form.setValue("endTime", "23:59");
                    }
                  }}
                />
              </FormControl>
              <FormLabel className="!mt-0">Cả ngày</FormLabel>
            </FormItem>
          )}
        />

        <div className="flex items-start gap-2">
          <FormField
            control={form.control}
            name="startDate"
            render={({ field, fieldState }) => (
              <FormItem className="flex-1">
                <FormLabel htmlFor="startDate">Bắt đầu</FormLabel>
                <FormControl>
                  <SingleDayPicker
                    id="startDate"
                    value={field.value}
                    onSelect={date => field.onChange(date as Date)}
                    placeholder="Chọn ngày"
                    data-invalid={fieldState.invalid}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {!allDay && (
            <FormField
              control={form.control}
              name="startTime"
              render={({ field, fieldState }) => (
                <FormItem className="w-28">
                  <FormLabel>Giờ</FormLabel>
                  <FormControl>
                    <Input type="time" data-invalid={fieldState.invalid} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
        </div>

        <div className="flex items-start gap-2">
          <FormField
            control={form.control}
            name="endDate"
            render={({ field, fieldState }) => (
              <FormItem className="flex-1">
                <FormLabel>Kết thúc</FormLabel>
                <FormControl>
                  <SingleDayPicker
                    value={field.value}
                    onSelect={date => field.onChange(date as Date)}
                    placeholder="Chọn ngày"
                    data-invalid={fieldState.invalid}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {!allDay && (
            <FormField
              control={form.control}
              name="endTime"
              render={({ field, fieldState }) => (
                <FormItem className="w-28">
                  <FormLabel>Giờ</FormLabel>
                  <FormControl>
                    <Input type="time" data-invalid={fieldState.invalid} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
        </div>

        <div className="flex items-start gap-2">
          <FormField
            control={form.control}
            name="repeat"
            render={({ field }) => (
              <FormItem className="flex-1">
                <FormLabel>Lặp lại</FormLabel>
                <FormControl>
                  <Select
                    value={field.value}
                    onValueChange={value => {
                      field.onChange(value);
                      // Weekly seeds the toggle row with the start date's weekday.
                      if (value === "weekly" && form.getValues("byDay").length === 0) {
                        const start = form.getValues("startDate");
                        if (start) form.setValue("byDay", [weekdayCodeOf(start)]);
                      }
                    }}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Không lặp" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">Không lặp</SelectItem>
                      <SelectItem value="daily">Hàng ngày</SelectItem>
                      <SelectItem value="weekly">Hàng tuần</SelectItem>
                    </SelectContent>
                  </Select>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {repeat !== "none" && (
            <FormField
              control={form.control}
              name="until"
              render={({ field }) => (
                <FormItem className="flex-1">
                  <FormLabel>Đến ngày (tùy chọn)</FormLabel>
                  <FormControl>
                    <SingleDayPicker value={field.value} onSelect={date => field.onChange(date)} placeholder="Mãi mãi" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
        </div>

        {repeat === "weekly" && (
          <FormField
            control={form.control}
            name="byDay"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Vào các thứ</FormLabel>
                <FormControl>
                  <div className="flex gap-1.5">
                    {WEEKDAYS.map(day => {
                      const active = field.value.includes(day.code);
                      return (
                        <button
                          key={day.code}
                          type="button"
                          aria-pressed={active}
                          onClick={() =>
                            field.onChange(
                              active ? field.value.filter(code => code !== day.code) : [...field.value, day.code],
                            )
                          }
                          className={cn(
                            "flex size-8 items-center justify-center rounded-full border text-xs font-medium transition-colors",
                            active ? "border-primary bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted",
                          )}
                        >
                          {day.label}
                        </button>
                      );
                    })}
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        )}

        <FormField
          control={form.control}
          name="disciplineId"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Discipline</FormLabel>
              <FormControl>
                <Select
                  value={field.value ?? NO_DISCIPLINE}
                  onValueChange={value => {
                    field.onChange(value === NO_DISCIPLINE ? undefined : value);
                    // The discipline is the "calendar": picking one adopts its color.
                    const discipline = disciplines.find(d => d.id === value);
                    if (discipline) form.setValue("color", discipline.color.toLowerCase() as TEventFormData["color"]);
                  }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Không thuộc discipline nào" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NO_DISCIPLINE}>Không</SelectItem>
                    {disciplines.map(d => (
                      <SelectItem key={d.id} value={d.id}>
                        <div className="flex items-center gap-2">
                          <div className={`size-3.5 rounded-full ${COLORS.find(c => c.value === d.color.toLowerCase())?.dot ?? "bg-neutral-600"}`} />
                          {d.name}
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="color"
          render={({ field, fieldState }) => (
            <FormItem>
              <FormLabel>Màu</FormLabel>
              <FormControl>
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger data-invalid={fieldState.invalid}>
                    <SelectValue placeholder="Chọn màu" />
                  </SelectTrigger>
                  <SelectContent>
                    {COLORS.map(color => (
                      <SelectItem key={color.value} value={color.value}>
                        <div className="flex items-center gap-2">
                          <div className={`size-3.5 rounded-full ${color.dot}`} />
                          {color.label}
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="description"
          render={({ field, fieldState }) => (
            <FormItem>
              <FormLabel>Ghi chú</FormLabel>
              <FormControl>
                <Textarea {...field} value={field.value ?? ""} data-invalid={fieldState.invalid} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
      </form>
    </Form>
  );
}
