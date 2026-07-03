import { format } from "date-fns";

/**
 * Client side of the RRULE subset the server speaks (RFC 5545:
 * FREQ=DAILY|WEEKLY, BYDAY, UNTIL date, INTERVAL, COUNT). The form only ever
 * BUILDS none/daily/weekly + BYDAY + UNTIL; parse tolerates the full subset so
 * a hand-written rule still round-trips through the edit dialog.
 */

export type TRepeat = "none" | "daily" | "weekly";

export const WEEKDAYS = [
  { code: "MO", label: "T2" },
  { code: "TU", label: "T3" },
  { code: "WE", label: "T4" },
  { code: "TH", label: "T5" },
  { code: "FR", label: "T6" },
  { code: "SA", label: "T7" },
  { code: "SU", label: "CN" },
] as const;

export type TWeekdayCode = (typeof WEEKDAYS)[number]["code"];

const DAY_ORDER: TWeekdayCode[] = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"];

export interface IRecurrenceFields {
  repeat: TRepeat;
  byDay: TWeekdayCode[];
  until?: Date;
}

/** JS getDay() (Sun=0) → RRULE code. */
export function weekdayCodeOf(date: Date): TWeekdayCode {
  return DAY_ORDER[(date.getDay() + 6) % 7];
}

/** Form fields → RRULE string; undefined for one-off events. */
export function buildRrule(fields: IRecurrenceFields, startDate: Date): string | undefined {
  if (fields.repeat === "none") return undefined;
  const parts = [`FREQ=${fields.repeat.toUpperCase()}`];
  if (fields.repeat === "weekly") {
    const days = fields.byDay.length > 0 ? fields.byDay : [weekdayCodeOf(startDate)];
    parts.push(`BYDAY=${DAY_ORDER.filter(d => days.includes(d)).join(",")}`);
  }
  if (fields.until) parts.push(`UNTIL=${format(fields.until, "yyyyMMdd")}`);
  return parts.join(";");
}

/** RRULE string → form fields. Unknown/absent → "none" so the form stays usable. */
export function parseRrule(rrule: string | undefined): IRecurrenceFields {
  if (!rrule) return { repeat: "none", byDay: [] };
  const parts = new Map(
    rrule.split(";").map(part => {
      const [key = "", value = ""] = part.split("=");
      return [key.toUpperCase(), value] as const;
    }),
  );
  const freq = parts.get("FREQ")?.toUpperCase();
  const repeat: TRepeat = freq === "DAILY" ? "daily" : freq === "WEEKLY" ? "weekly" : "none";
  const byDay = (parts.get("BYDAY") ?? "")
    .split(",")
    .filter((d): d is TWeekdayCode => DAY_ORDER.includes(d as TWeekdayCode));
  const untilRaw = parts.get("UNTIL");
  const until =
    untilRaw && /^\d{8}/.test(untilRaw)
      ? new Date(Number(untilRaw.slice(0, 4)), Number(untilRaw.slice(4, 6)) - 1, Number(untilRaw.slice(6, 8)))
      : undefined;
  return { repeat, byDay, until };
}

/** "Hàng tuần: T2, T4 · đến 20/12/2026" — the details-dialog line. */
export function humanizeRrule(rrule: string): string {
  const { repeat, byDay, until } = parseRrule(rrule);
  if (repeat === "none") return rrule; // out-of-subset rule: show it raw rather than lie
  let text = repeat === "daily" ? "Hàng ngày" : "Hàng tuần";
  if (repeat === "weekly" && byDay.length > 0) {
    text += `: ${byDay.map(code => WEEKDAYS.find(d => d.code === code)?.label ?? code).join(", ")}`;
  }
  if (until) text += ` · đến ${format(until, "d/M/yyyy")}`;
  return text;
}
