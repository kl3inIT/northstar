package com.northstar.core.calendar;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The RFC 5545 RRULE subset this app speaks: {@code FREQ=DAILY|WEEKLY} with
 * {@code INTERVAL}, {@code BYDAY} (weekly), {@code UNTIL} (date or UTC
 * datetime, inclusive) and {@code COUNT}. The string format is standard so a
 * later Google Calendar sync reads the same column; anything outside the
 * subset is rejected at write time rather than silently ignored.
 *
 * <p>Expansion is local-time based: every occurrence keeps the master's local
 * time-of-day in the given zone, which is what "lớp 19:00 mỗi thứ 2" means.
 */
final class RecurrenceRule {

    private enum Freq { DAILY, WEEKLY }

    /** Guard against a runaway rule (e.g. INTERVAL=1 over years): far above any real window. */
    private static final int MAX_ITERATIONS = 5_000;

    private static final DateTimeFormatter UNTIL_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter UNTIL_DATETIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final Freq freq;
    private final int interval;
    private final Set<DayOfWeek> byDay;
    private final LocalDate untilDate;      // inclusive local date, or null
    private final Instant untilInstant;     // inclusive instant (UNTIL=...Z form), or null
    private final Integer count;            // total occurrences from the series start, or null

    private RecurrenceRule(Freq freq, int interval, Set<DayOfWeek> byDay,
            LocalDate untilDate, Instant untilInstant, Integer count) {
        this.freq = freq;
        this.interval = interval;
        this.byDay = byDay;
        this.untilDate = untilDate;
        this.untilInstant = untilInstant;
        this.count = count;
    }

    /** @throws IllegalArgumentException on anything outside the supported subset */
    static RecurrenceRule parse(String rrule) {
        Freq freq = null;
        int interval = 1;
        Set<DayOfWeek> byDay = EnumSet.noneOf(DayOfWeek.class);
        LocalDate untilDate = null;
        Instant untilInstant = null;
        Integer count = null;

        for (String part : rrule.strip().split(";")) {
            int eq = part.indexOf('=');
            if (eq < 1) {
                throw new IllegalArgumentException("Malformed RRULE part: " + part);
            }
            String key = part.substring(0, eq).toUpperCase(Locale.ROOT);
            String value = part.substring(eq + 1);
            switch (key) {
                case "FREQ" -> freq = switch (value.toUpperCase(Locale.ROOT)) {
                    case "DAILY" -> Freq.DAILY;
                    case "WEEKLY" -> Freq.WEEKLY;
                    default -> throw new IllegalArgumentException("Unsupported FREQ: " + value);
                };
                case "INTERVAL" -> {
                    interval = Integer.parseInt(value);
                    if (interval < 1) {
                        throw new IllegalArgumentException("INTERVAL must be positive: " + value);
                    }
                }
                case "BYDAY" -> {
                    for (String day : value.split(",")) {
                        byDay.add(dayOfWeek(day));
                    }
                }
                case "UNTIL" -> {
                    if (value.contains("T")) {
                        untilInstant = Instant.from(UNTIL_DATETIME.parse(value));
                    } else {
                        untilDate = LocalDate.parse(value, UNTIL_DATE);
                    }
                }
                case "COUNT" -> {
                    count = Integer.parseInt(value);
                    if (count < 1) {
                        throw new IllegalArgumentException("COUNT must be positive: " + value);
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported RRULE part: " + key);
            }
        }
        if (freq == null) {
            throw new IllegalArgumentException("RRULE must declare FREQ");
        }
        if (freq == Freq.DAILY && !byDay.isEmpty()) {
            throw new IllegalArgumentException("BYDAY is only supported with FREQ=WEEKLY");
        }
        return new RecurrenceRule(freq, interval, byDay, untilDate, untilInstant, count);
    }

    /**
     * Occurrence starts whose span {@code [start, start + duration)} overlaps
     * {@code [from, to)}, in chronological order. {@code seedStart} anchors the
     * series and is always its first candidate; occurrences keep its local
     * time-of-day in {@code zone}.
     */
    List<Instant> occurrencesBetween(Instant seedStart, Duration duration, ZoneId zone, Instant from, Instant to) {
        ZonedDateTime seed = seedStart.atZone(zone);
        LocalTime time = seed.toLocalTime();
        List<Instant> result = new ArrayList<>();
        int emitted = 0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            LocalDate date = candidateDate(seed, i);
            if (date == null) {
                break;
            }
            Instant start = ZonedDateTime.of(date, time, zone).toInstant();
            if (start.isBefore(seedStart)) {
                continue;
            }
            if (pastUntil(date, start)) {
                break;
            }
            emitted++;
            if (count != null && emitted > count) {
                break;
            }
            if (!start.isBefore(to)) {
                break;
            }
            if (start.plus(duration).isAfter(from)) {
                result.add(start);
            }
        }
        return result;
    }

    /** True when the given start instant is one this rule generates for the seed. */
    boolean generates(Instant seedStart, ZoneId zone, Instant occurrenceStart) {
        return occurrencesBetween(seedStart, Duration.ofNanos(1), zone,
                occurrenceStart, occurrenceStart.plusNanos(1)).contains(occurrenceStart);
    }

    /**
     * The i-th candidate date of the series (before UNTIL/COUNT trimming).
     * Weekly walks Monday→Sunday inside each INTERVAL-spaced week so results
     * come out chronological.
     */
    private LocalDate candidateDate(ZonedDateTime seed, int i) {
        if (freq == Freq.DAILY) {
            return seed.toLocalDate().plusDays((long) i * interval);
        }
        List<DayOfWeek> days = byDay.isEmpty()
                ? List.of(seed.getDayOfWeek())
                : byDay.stream().sorted().toList();
        LocalDate weekMonday = seed.toLocalDate().minusDays(seed.getDayOfWeek().getValue() - 1L);
        int week = i / days.size();
        DayOfWeek day = days.get(i % days.size());
        return weekMonday.plusWeeks((long) week * interval).plusDays(day.getValue() - 1L);
    }

    private boolean pastUntil(LocalDate date, Instant start) {
        return (untilDate != null && date.isAfter(untilDate))
                || (untilInstant != null && start.isAfter(untilInstant));
    }

    private static DayOfWeek dayOfWeek(String code) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "MO" -> DayOfWeek.MONDAY;
            case "TU" -> DayOfWeek.TUESDAY;
            case "WE" -> DayOfWeek.WEDNESDAY;
            case "TH" -> DayOfWeek.THURSDAY;
            case "FR" -> DayOfWeek.FRIDAY;
            case "SA" -> DayOfWeek.SATURDAY;
            case "SU" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unsupported BYDAY value: " + code);
        };
    }
}
