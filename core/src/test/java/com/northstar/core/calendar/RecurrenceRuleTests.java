package com.northstar.core.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure expansion semantics of the RRULE subset. All cases anchor at a Monday
 * 19:00 VN class — the "Lớp HSK 4 thứ 2 & thứ 4" shape recurrence exists for.
 */
class RecurrenceRuleTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Monday 2026-07-06 19:00 ICT
    private static final Instant SEED = ZonedDateTime.of(2026, 7, 6, 19, 0, 0, 0, ZONE).toInstant();
    private static final Duration NINETY_MIN = Duration.ofMinutes(90);

    private static Instant ict(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant();
    }

    private static List<Instant> expand(String rrule, Instant from, Instant to) {
        return RecurrenceRule.parse(rrule).occurrencesBetween(SEED, NINETY_MIN, ZONE, from, to);
    }

    @Test
    void weeklyByDayExpandsMondayAndWednesdayAtSeedLocalTime() {
        List<Instant> starts = expand("FREQ=WEEKLY;BYDAY=MO,WE",
                ict(2026, 7, 6, 0, 0), ict(2026, 7, 20, 0, 0));

        assertThat(starts).containsExactly(
                ict(2026, 7, 6, 19, 0), ict(2026, 7, 8, 19, 0),
                ict(2026, 7, 13, 19, 0), ict(2026, 7, 15, 19, 0));
    }

    @Test
    void occurrencesBeforeTheSeriesAnchorNeverAppear() {
        // Series anchored on a WEDNESDAY, but BYDAY also names Monday: the
        // Monday of the anchor week lies before the anchor and must not appear.
        Instant wednesdaySeed = ict(2026, 7, 8, 19, 0);
        List<Instant> starts = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO,WE")
                .occurrencesBetween(wednesdaySeed, NINETY_MIN, ZONE, ict(2026, 6, 29, 0, 0), ict(2026, 7, 16, 0, 0));

        assertThat(starts).containsExactly(
                ict(2026, 7, 8, 19, 0), ict(2026, 7, 13, 19, 0), ict(2026, 7, 15, 19, 0));
    }

    @Test
    void untilDateIsInclusiveAndStopsTheSeries() {
        List<Instant> starts = expand("FREQ=WEEKLY;BYDAY=MO;UNTIL=20260720",
                ict(2026, 7, 1, 0, 0), ict(2026, 8, 31, 0, 0));

        assertThat(starts).containsExactly(
                ict(2026, 7, 6, 19, 0), ict(2026, 7, 13, 19, 0), ict(2026, 7, 20, 19, 0));
    }

    @Test
    void intervalSkipsWeeksAndCountCapsTotalOccurrences() {
        assertThat(expand("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO", ict(2026, 7, 1, 0, 0), ict(2026, 8, 10, 0, 0)))
                .containsExactly(ict(2026, 7, 6, 19, 0), ict(2026, 7, 20, 19, 0), ict(2026, 8, 3, 19, 0));

        // COUNT counts from the series start, even when the window begins later.
        assertThat(expand("FREQ=WEEKLY;BYDAY=MO;COUNT=3", ict(2026, 7, 14, 0, 0), ict(2026, 8, 31, 0, 0)))
                .containsExactly(ict(2026, 7, 20, 19, 0));
    }

    @Test
    void dailyStepsCalendarDaysAndWeeklyWithoutByDayUsesTheSeedWeekday() {
        assertThat(expand("FREQ=DAILY", ict(2026, 7, 6, 0, 0), ict(2026, 7, 9, 0, 0)))
                .containsExactly(ict(2026, 7, 6, 19, 0), ict(2026, 7, 7, 19, 0), ict(2026, 7, 8, 19, 0));

        assertThat(expand("FREQ=WEEKLY", ict(2026, 7, 6, 0, 0), ict(2026, 7, 20, 0, 0)))
                .containsExactly(ict(2026, 7, 6, 19, 0), ict(2026, 7, 13, 19, 0));
    }

    @Test
    void spanOverlapCountsAnOccurrenceStartingBeforeTheWindow() {
        // Occurrence 19:00–20:30; window opens 19:30 the same evening.
        List<Instant> starts = expand("FREQ=WEEKLY;BYDAY=MO",
                ict(2026, 7, 6, 19, 30), ict(2026, 7, 7, 0, 0));

        assertThat(starts).containsExactly(ict(2026, 7, 6, 19, 0));
    }

    @Test
    void rejectsEverythingOutsideTheSubset() {
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceRule.parse("FREQ=MONTHLY"));
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceRule.parse("BYDAY=MO"));
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceRule.parse("FREQ=WEEKLY;BYSETPOS=1"));
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=XX"));
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=0"));
        assertThatIllegalArgumentException().isThrownBy(() -> RecurrenceRule.parse("FREQ=DAILY;BYDAY=MO"));
    }
}
