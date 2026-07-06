package com.northstar.core.alignment;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskStatus;
import com.northstar.core.task.TaskSummary;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Drafts the daily/weekly Alignment review: facts are assembled in code (never
 * hallucinated), the LLM only writes the short commentary on top, and the result
 * is upserted as a {@code Journal/} note keyed by a deterministic title — hitting
 * "generate" again refreshes the same note instead of stacking duplicates.
 *
 * <p>Deliberately NOT a component: the delivering app defines the bean and its
 * {@link ChatClient} (see the api's AlignmentConfig), so mcp/worker boot without
 * an LLM configured.
 */
public class AlignmentService {

    private static final Logger log = LoggerFactory.getLogger(AlignmentService.class);

    private static final String FOLDER = "Journal";
    private static final int MAX_STAGING_LINES = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    // Step-back call (prompt-engineering pattern): analysis is separated from
    // writing, so the writer starts from observations instead of a raw table.
    private static final String OBSERVATIONS_SYSTEM = """
            You are the analysis step before a personal review is written.
            From the numbers in the user message, extract the 2-4 observations that
            MATTER — patterns, risks, wins. Examples of what qualifies: the same task
            slipping repeatedly, all overdue work clustering in one area, a day/week
            with real output, tomorrow being overloaded.
            One short line each, grounded ONLY in the numbers (quote titles verbatim).
            No advice, no filler, no restating totals that carry no signal.
            If the numbers are nearly empty, output exactly one line saying so.
            """;

    // Shared contrastive example: the writer must read patterns, not recite rows.
    private static final String COMMENTARY_EXAMPLE = """

            <example>
            Bad commentary (recites the table): "You completed 0 tasks today and have
            8 open tasks that are overdue."
            Good commentary (reads the pattern): "A blank day — and 'Ôn 50 từ vựng
            IELTS' has now slipped 6 days straight, so it is either too big or no
            longer worth doing; shrink it or drop it."
            </example>
            """;

    private static final String DAILY_SYSTEM = """
            You are the end-of-day review companion inside Northstar, a personal-growth OS.
            The user does NOT want to write the journal themselves — you draft it, they
            only read. The user message is the day's real numbers plus pre-extracted
            observations; build the commentary AROUND those observations. Fill the two
            fields:
            - commentary: 3-5 sentences IN ENGLISH (quote task/note titles verbatim in
              whatever language they are in), direct and honest, no flattery, no filler.
              An honest read of the day (what meaningful got done, what slipped); if a
              task is overdue by several days, name it outright; if notes are waiting
              for review (Staging), one sentence about it. If the numbers are nearly
              empty, exactly 2 short sentences — do not pad. No headings, do not
              restate the numbers table, never invent work that is not in the numbers.
            - priority: the ONE item for tomorrow, picked from the numbers.
            """ + COMMENTARY_EXAMPLE;

    private static final String WEEKLY_SYSTEM = """
            You are the end-of-week review companion inside Northstar, a personal-growth OS.
            The user does NOT want to write the journal themselves — you draft it, they
            only read. The user message is the week's real numbers plus pre-extracted
            observations; build the commentary AROUND those observations. Fill the two
            fields:
            - commentary: 5-7 sentences IN ENGLISH (quote task/note titles verbatim in
              whatever language they are in), direct and honest, no flattery, no filler.
              What the week delivered and what slipped; call out a pattern if you see
              one (the same task sliding repeatedly, work piling up at the weekend); if
              notes are waiting for review (Staging), one sentence about it. If the
              numbers are nearly empty, exactly 2 short sentences — do not pad. No
              headings, do not restate the numbers table, never invent work that is
              not in the numbers.
            - priority: the ONE item for next week, picked from the numbers.
            """ + COMMENTARY_EXAMPLE;

    private final ChatClient chat;
    private final TaskService tasks;
    private final CalendarEventService events;
    private final NoteService notes;

    public AlignmentService(ChatClient chat, TaskService tasks, CalendarEventService events,
            NoteService notes) {
        this.chat = chat;
        this.tasks = tasks;
        this.events = events;
        this.notes = notes;
    }

    /** Today's review note if it was already generated (zone-local day). */
    public Optional<NoteDetail> findDaily(ZoneId zone) {
        return notes.findByTitle(dailyTitle(LocalDate.now(zone)));
    }

    /** This week's review note if it was already generated (ISO week, zone-local). */
    public Optional<NoteDetail> findWeekly(ZoneId zone) {
        return notes.findByTitle(weeklyTitle(LocalDate.now(zone)));
    }

    /** (Re)drafts today's review — a two-step chain — and upserts the Journal note. */
    public NoteDetail generateDaily(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        String facts = dailyFacts(today, zone);
        String body = commentary(DAILY_SYSTEM, facts, "Tomorrow's priority") + "\n\n---\n\n" + facts;
        return upsert(dailyTitle(today), List.of("alignment", "daily"), body);
    }

    /** (Re)drafts this week's review — a two-step chain — and upserts the Journal note. */
    public NoteDetail generateWeekly(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        String facts = weeklyFacts(today);
        String body = commentary(WEEKLY_SYSTEM, facts, "Next week's priority") + "\n\n---\n\n" + facts;
        return upsert(weeklyTitle(today), List.of("alignment", "weekly"), body);
    }

    // --- internals ---------------------------------------------------------

    private String dailyTitle(LocalDate day) {
        return "Daily review " + day;
    }

    private String weeklyTitle(LocalDate day) {
        WeekFields iso = WeekFields.ISO;
        return "Weekly review %d-W%02d".formatted(
                day.get(iso.weekBasedYear()), day.get(iso.weekOfWeekBasedYear()));
    }

    /**
     * The AI paragraph: step-back call extracts observations, the writer call turns
     * them into commentary + a priority (structured, so the priority line can never
     * drift from its format). Each step degrades independently — observations
     * failing just means the writer sees raw numbers; the writer failing ships the
     * facts-only note, exactly as before.
     */
    private String commentary(String system, String facts, String priorityLabel) {
        String user = facts;
        String observations = observations(facts);
        if (!observations.isBlank()) {
            user = facts + "\n\n## Observations (pre-extracted)\n" + observations;
        }
        try {
            ReviewCommentary draft = chat.prompt().system(system).user(user).call()
                    .entity(ReviewCommentary.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);
            if (draft != null && draft.commentary() != null && !draft.commentary().isBlank()) {
                String priority = draft.priority() == null || draft.priority().isBlank()
                        ? "" : "\n\n**" + priorityLabel + ":** " + draft.priority().strip();
                return draft.commentary().strip() + priority;
            }
        } catch (RuntimeException e) {
            log.warn("Alignment commentary failed; falling back to facts-only note", e);
        }
        return "*Couldn't generate the AI commentary this time — the raw numbers are below.*";
    }

    /** Step-back observations; "" on any failure so the chain degrades to single-call. */
    private String observations(String facts) {
        try {
            String text = chat.prompt().system(OBSERVATIONS_SYSTEM).user(facts).call().content();
            return text == null ? "" : text.strip();
        } catch (RuntimeException e) {
            log.warn("Alignment observations step failed; writing commentary from raw numbers", e);
            return "";
        }
    }

    private NoteDetail upsert(String title, List<String> tags, String markdown) {
        return notes.findByTitle(title)
                .map(existing -> notes.update(existing.id(), title, FOLDER, markdown, tags, null))
                .orElseGet(() -> notes.create(title, FOLDER, markdown, tags, NoteStatus.RESOURCE));
    }

    private String dailyFacts(LocalDate today, ZoneId zone) {
        List<TaskSummary> todayList = tasks.today(zone);
        List<TaskSummary> done = todayList.stream()
                .filter(t -> t.status() == TaskStatus.DONE).toList();
        List<TaskSummary> overdue = todayList.stream()
                .filter(t -> t.status() == TaskStatus.OPEN
                        && t.dueDate() != null && t.dueDate().isBefore(today))
                .toList();
        List<TaskSummary> dueToday = todayList.stream()
                .filter(t -> t.status() == TaskStatus.OPEN && today.equals(t.dueDate())).toList();
        List<TaskSummary> tomorrow = tasks.range(today.plusDays(1), today.plusDays(1)).stream()
                .filter(t -> t.status() == TaskStatus.OPEN).toList();
        List<CalendarEventSummary> tomorrowEvents = events.range(
                today.plusDays(1).atStartOfDay(zone).toInstant(),
                today.plusDays(2).atStartOfDay(zone).toInstant(), zone);
        List<NoteSummary> staging = stagingNotes();

        StringBuilder sb = new StringBuilder("## The numbers\n");
        section(sb, "Done today (%d)".formatted(done.size()),
                done.stream().map(t -> "- " + t.title()).toList());
        section(sb, "Still open (%d, %d overdue)".formatted(overdue.size() + dueToday.size(), overdue.size()),
                java.util.stream.Stream.concat(
                        overdue.stream().map(t -> "- %s — overdue %s"
                                .formatted(t.title(), count(ChronoUnit.DAYS.between(t.dueDate(), today), "day"))),
                        dueToday.stream().map(t -> "- %s — due today%s"
                                .formatted(t.title(), t.dueTime() == null ? "" : " " + TIME.format(t.dueTime()))))
                        .toList());
        section(sb, "Notes awaiting review / Staging (%d)".formatted(staging.size()),
                staging.stream().map(n -> "- " + n.title()).toList());
        section(sb, "Tomorrow (%s, %s)".formatted(
                count(tomorrow.size(), "task"), count(tomorrowEvents.size(), "event")),
                java.util.stream.Stream.concat(
                        tomorrow.stream().map(t -> "- Task: %s%s"
                                .formatted(t.title(), t.dueTime() == null ? "" : " (" + TIME.format(t.dueTime()) + ")")),
                        tomorrowEvents.stream().map(e -> "- " + eventLine(e, zone)))
                        .toList());
        return sb.toString().stripTrailing();
    }

    private String weeklyFacts(LocalDate today) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<TaskSummary> week = tasks.range(monday, monday.plusDays(6));
        List<TaskSummary> done = week.stream().filter(t -> t.status() == TaskStatus.DONE).toList();
        List<TaskSummary> open = week.stream().filter(t -> t.status() == TaskStatus.OPEN).toList();
        List<TaskSummary> nextWeek = tasks.range(monday.plusDays(7), monday.plusDays(13)).stream()
                .filter(t -> t.status() == TaskStatus.OPEN).toList();
        List<NoteSummary> staging = stagingNotes();

        StringBuilder sb = new StringBuilder("## The numbers, week %s → %s\n".formatted(monday, monday.plusDays(6)));
        section(sb, "Done this week (%d)".formatted(done.size()),
                done.stream().map(t -> "- %s (due %s)".formatted(t.title(), t.dueDate())).toList());
        section(sb, "Not done (%d)".formatted(open.size()),
                open.stream().map(t -> "- %s (due %s)".formatted(t.title(), t.dueDate())).toList());
        section(sb, "Notes awaiting review / Staging (%d)".formatted(staging.size()),
                staging.stream().map(n -> "- " + n.title()).toList());
        section(sb, "Next week (%s with a due date)".formatted(count(nextWeek.size(), "task")),
                nextWeek.stream().map(t -> "- %s (due %s)".formatted(t.title(), t.dueDate())).toList());
        return sb.toString().stripTrailing();
    }

    private List<NoteSummary> stagingNotes() {
        return notes.listByStatus(NoteStatus.STAGING,
                        PageRequest.of(0, MAX_STAGING_LINES, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    private String eventLine(CalendarEventSummary event, ZoneId zone) {
        if (event.allDay()) {
            return "All day: " + event.title();
        }
        LocalDateTime start = LocalDateTime.ofInstant(event.startAt(), zone);
        LocalDateTime end = LocalDateTime.ofInstant(event.endAt(), zone);
        return "%s–%s %s".formatted(TIME.format(start), TIME.format(end), event.title());
    }

    private static String count(long n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private static void section(StringBuilder sb, String heading, List<String> lines) {
        sb.append("\n**").append(heading).append(":**\n");
        if (lines.isEmpty()) {
            sb.append("- (none)\n");
        } else {
            lines.forEach(line -> sb.append(line).append('\n'));
        }
    }
}
