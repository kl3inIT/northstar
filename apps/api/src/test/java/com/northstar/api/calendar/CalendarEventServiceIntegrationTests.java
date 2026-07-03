package com.northstar.api.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.shared.ColorName;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Recurring-calendar acceptance against a real Postgres: a weekly series
 * expands into occurrences inside the window, "chỉ buổi này" drops exactly one
 * occurrence (persisted as an exception row), "cả chuỗi" removes everything,
 * and a bad rrule never reaches the table.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@Testcontainers
class CalendarEventServiceIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    CalendarEventService events;

    private static Instant ict(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant();
    }

    @Test
    void weeklySeriesExpandsCancelsOneOccurrenceAndDeletesAsAWhole() {
        // Lớp HSK 4: Mon & Wed 19:00–20:30, anchored Monday 2026-07-06.
        CalendarEventSummary master = events.create("Lớp HSK 4", null,
                ict(2026, 7, 6, 19, 0), ict(2026, 7, 6, 20, 30),
                false, ColorName.YELLOW, null, "FREQ=WEEKLY;BYDAY=MO,WE");

        List<CalendarEventSummary> twoWeeks = events.range(
                ict(2026, 7, 6, 0, 0), ict(2026, 7, 20, 0, 0), ZONE);
        assertThat(twoWeeks).extracting(CalendarEventSummary::startAt).containsExactly(
                ict(2026, 7, 6, 19, 0), ict(2026, 7, 8, 19, 0),
                ict(2026, 7, 13, 19, 0), ict(2026, 7, 15, 19, 0));
        assertThat(twoWeeks).allSatisfy(occurrence -> {
            assertThat(occurrence.id()).isEqualTo(master.id());
            assertThat(occurrence.rrule()).isEqualTo("FREQ=WEEKLY;BYDAY=MO,WE");
        });

        // "Chỉ buổi này": Wednesday 8/7 is skipped, the series survives.
        events.cancelOccurrence(master.id(), ict(2026, 7, 8, 19, 0));
        assertThat(events.range(ict(2026, 7, 6, 0, 0), ict(2026, 7, 20, 0, 0), ZONE))
                .extracting(CalendarEventSummary::startAt)
                .containsExactly(ict(2026, 7, 6, 19, 0), ict(2026, 7, 13, 19, 0), ict(2026, 7, 15, 19, 0));

        // "Cả chuỗi": the master (and its exception rows, by cascade) go away.
        events.delete(master.id());
        assertThat(events.range(ict(2026, 7, 6, 0, 0), ict(2026, 7, 20, 0, 0), ZONE))
                .extracting(CalendarEventSummary::id).doesNotContain(master.id());
    }

    @Test
    void recurringSeriesMixWithOneOffEventsAndRejectBadInput() {
        CalendarEventSummary oneOff = events.create("Họp nhóm", null,
                ict(2026, 8, 4, 9, 0), ict(2026, 8, 4, 10, 0), false, ColorName.BLUE, null, null);
        CalendarEventSummary series = events.create("Gym", null,
                ict(2026, 8, 3, 6, 0), ict(2026, 8, 3, 7, 0), false, ColorName.GREEN, null,
                "FREQ=WEEKLY;BYDAY=MO;UNTIL=20260810");

        List<CalendarEventSummary> august = events.range(
                ict(2026, 8, 1, 0, 0), ict(2026, 8, 31, 0, 0), ZONE);
        assertThat(august.stream().filter(e -> e.id().equals(series.id())))
                .extracting(CalendarEventSummary::startAt)
                .containsExactly(ict(2026, 8, 3, 6, 0), ict(2026, 8, 10, 6, 0)); // UNTIL inclusive
        assertThat(august).extracting(CalendarEventSummary::id).contains(oneOff.id());

        assertThatIllegalArgumentException()
                .describedAs("unsupported rrule must be rejected at write time")
                .isThrownBy(() -> events.create("Bad", null,
                        ict(2026, 8, 1, 8, 0), ict(2026, 8, 1, 9, 0),
                        false, ColorName.RED, null, "FREQ=MONTHLY"));

        assertThatIllegalArgumentException()
                .describedAs("a series cannot be rescheduled as a block")
                .isThrownBy(() -> events.reschedule(series.id(),
                        ict(2026, 8, 3, 7, 0), ict(2026, 8, 3, 8, 0)));

        assertThatIllegalArgumentException()
                .describedAs("cancelOccurrence is recurring-only")
                .isThrownBy(() -> events.cancelOccurrence(oneOff.id(), oneOff.startAt()));
    }
}
