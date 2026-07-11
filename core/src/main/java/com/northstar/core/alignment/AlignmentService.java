package com.northstar.core.alignment;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.finance.FinanceService;
import com.northstar.core.study.StudyKind;
import com.northstar.core.study.StudyService;
import com.northstar.core.study.StudySessionSummary;
import com.northstar.core.study.StudySummary;
import com.northstar.core.study.VocabCardSummary;
import com.northstar.core.study.VocabService;
import com.northstar.core.finance.SubscriptionSummary;
import com.northstar.core.finance.TransactionSummary;
import com.northstar.core.finance.TransactionType;
import com.northstar.core.note.NoteDetail;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteStatus;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskStatus;
import com.northstar.core.task.TaskSummary;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.northstar.core.ai.AiClientRouter;
import com.northstar.core.ai.AiRoute;
import com.northstar.core.ai.AiTask;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
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
              notes are waiting for review (Staging), one sentence about it. If a
              "Money this week" section is present, ONE sentence comparing spending to
              the typical week and naming the one-off purchases — descriptive and
              factual, never scolding, never advice about spending less; if it lists
              subscriptions due soon, name them and their dates in that sentence. If a
              "Study this week" section is present, ONE sentence describing the effort
              versus last week and naming the most-neglected skill and any words
              slipping away — descriptive, never scolding. If the
              numbers are nearly empty, exactly 2 short sentences — do not pad. No
              headings, do not restate the numbers table, never invent work that is
              not in the numbers.
            - priority: the ONE item for next week, picked from the numbers.
            """ + COMMENTARY_EXAMPLE;

    private final AiClientRouter ai;
    private final TaskService tasks;
    private final CalendarEventService events;
    private final NoteService notes;
    private final FinanceService finance;
    private final StudyService study;
    private final VocabService vocab;

    public AlignmentService(AiClientRouter ai, TaskService tasks, CalendarEventService events,
            NoteService notes, FinanceService finance, StudyService study, VocabService vocab) {
        this.ai = ai;
        this.tasks = tasks;
        this.events = events;
        this.notes = notes;
        this.finance = finance;
        this.study = study;
        this.vocab = vocab;
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
            AiRoute route = ai.route(AiTask.ALIGNMENT);
            ReviewCommentary draft = ai.client(route).prompt()
                    .options(ChatOptions.builder().model(route.modelId()))
                    .system(system).user(user).call()
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
            AiRoute route = ai.route(AiTask.ALIGNMENT);
            String text = ai.client(route).prompt()
                    .options(ChatOptions.builder().model(route.modelId()))
                    .system(OBSERVATIONS_SYSTEM).user(facts).call().content();
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
                Stream.concat(
                        overdue.stream().map(t -> "- %s — overdue %s"
                                .formatted(t.title(), count(ChronoUnit.DAYS.between(t.dueDate(), today), "day"))),
                        dueToday.stream().map(t -> "- %s — due today%s"
                                .formatted(t.title(), t.dueTime() == null ? "" : " " + TIME.format(t.dueTime()))))
                        .toList());
        section(sb, "Notes awaiting review / Staging (%d)".formatted(staging.size()),
                staging.stream().map(n -> "- " + n.title()).toList());
        section(sb, "Tomorrow (%s, %s)".formatted(
                count(tomorrow.size(), "task"), count(tomorrowEvents.size(), "event")),
                Stream.concat(
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
        spendingSection(sb, monday);
        studySection(sb, monday);
        return sb.toString().stripTrailing();
    }

    /**
     * The week's study effort next to last week's — the same descriptive-norm
     * contract as money (reference, never a quota) — plus the vocabulary
     * memory's at-risk words so the review reinforces retrieval instead of
     * re-exposure. Silent log and empty trainer = no section; study never nags
     * someone who is not tracking it.
     */
    private void studySection(StringBuilder sb, LocalDate monday) {
        StudySummary week = study.summary(monday);
        List<VocabCardSummary> slipping = vocab.atRisk(3, null).stream()
                .filter(card -> card.recallProbability() < 0.7)
                .toList();
        if (week.sessionCount() == 0 && week.previousWeekMinutes() == 0 && slipping.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        if (week.sessionCount() > 0) {
            String bySkill = week.bySkill().stream()
                    .map(e -> "%s %s".formatted(e.skill(), minutes(e.minutes())))
                    .collect(Collectors.joining(", "));
            lines.add("- Sessions: %d%s".formatted(week.sessionCount(),
                    bySkill.isEmpty() ? "" : " — " + bySkill));
        }
        List<StudySessionSummary> mocks = study.sessions(monday, monday.plusDays(6)).stream()
                .filter(s -> s.kind() == StudyKind.MOCK && s.scoreRaw() != null)
                .toList();
        if (!mocks.isEmpty()) {
            lines.add("- Mock results: " + mocks.stream()
                    .map(s -> "%s %d/%d".formatted(s.skill(), s.scoreRaw(), s.scoreMax()))
                    .collect(Collectors.joining("; ")));
        }
        if (!slipping.isEmpty()) {
            lines.add("- Words slipping away (recall <70%): " + slipping.stream()
                    .map(card -> "%s (%d%%)".formatted(card.front(),
                            Math.round(card.recallProbability() * 100)))
                    .collect(Collectors.joining(", ")));
        }
        section(sb, "Study this week (%s, last week %s)"
                .formatted(minutes(week.totalMinutes()), minutes(week.previousWeekMinutes())),
                lines);
    }

    private static String minutes(int total) {
        int hours = total / 60;
        int rest = total % 60;
        if (hours == 0) {
            return rest + "m";
        }
        return rest == 0 ? hours + "h" : "%dh %02dm".formatted(hours, rest);
    }

    /**
     * The week's money, in the one feedback structure with experimental support
     * (Huebner 2020; Sussman &amp; Alter 2012): ordinary category totals AND the
     * one-off purchases aggregated, next to a typical-week reference inferred
     * from the ledger (median of the 4 prior weeks) — a descriptive norm, not a
     * budget. Silent ledger (no entries this week, no history) = no section at
     * all; finance never nags someone who is not tracking money.
     */
    private void spendingSection(StringBuilder sb, LocalDate monday) {
        List<TransactionSummary> week = finance.range(monday, monday.plusDays(6));
        long typical = finance.typicalWeekExpense(monday);
        // Recurring charges due through the end of NEXT week — the digest content
        // users consistently rate highest is "what's about to charge me". Charges
        // post automatically when due; this line is awareness, not a chore.
        // Cancel-reminder dates in the window are called out too (a reminder task
        // exists as well — the review reinforces it).
        List<SubscriptionSummary> dueSoon = finance.subscriptionsDueBy(monday.plusDays(13));
        List<SubscriptionSummary> cancelSoon = finance.subscriptions().stream()
                .filter(SubscriptionSummary::active)
                .filter(s -> s.cancelReminderOn() != null && !s.cancelReminderOn().isBefore(monday)
                        && !s.cancelReminderOn().isAfter(monday.plusDays(13)))
                .toList();
        if (week.isEmpty() && typical == 0 && dueSoon.isEmpty() && cancelSoon.isEmpty()) {
            return;
        }
        List<TransactionSummary> expenses = week.stream()
                .filter(t -> t.type() == TransactionType.EXPENSE).toList();
        long spent = expenses.stream().mapToLong(TransactionSummary::amount).sum();
        long income = week.stream().filter(t -> t.type() == TransactionType.INCOME)
                .mapToLong(TransactionSummary::amount).sum();
        List<TransactionSummary> oneOffs = expenses.stream()
                .filter(TransactionSummary::exceptional).toList();
        long ordinary = spent - oneOffs.stream().mapToLong(TransactionSummary::amount).sum();
        String topCategories = expenses.stream()
                .filter(t -> !t.exceptional())
                .collect(Collectors.groupingBy(TransactionSummary::category,
                        Collectors.summingLong(TransactionSummary::amount)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> "%s %s".formatted(e.getKey(), vnd(e.getValue())))
                .collect(Collectors.joining(", "));

        List<String> lines = new ArrayList<>();
        lines.add("- Ordinary spending: %s₫%s".formatted(vnd(ordinary),
                topCategories.isEmpty() ? "" : " — top: " + topCategories));
        if (oneOffs.isEmpty()) {
            lines.add("- One-offs: (none)");
        } else {
            lines.add("- One-offs (%d, total %s₫): %s".formatted(oneOffs.size(),
                    vnd(oneOffs.stream().mapToLong(TransactionSummary::amount).sum()),
                    oneOffs.stream().map(t -> "%s %s₫".formatted(t.description(), vnd(t.amount())))
                            .collect(Collectors.joining("; "))));
        }
        if (income > 0) {
            lines.add("- Income: %s₫".formatted(vnd(income)));
        }
        if (!dueSoon.isEmpty()) {
            lines.add("- Subscriptions charging soon (auto-posted): " + dueSoon.stream()
                    .map(s -> "%s %s₫ (%s)".formatted(s.name(), vnd(s.amount()), s.nextDueOn()))
                    .collect(Collectors.joining("; ")));
        }
        if (!cancelSoon.isEmpty()) {
            lines.add("- Cancel-by dates this window: " + cancelSoon.stream()
                    .map(s -> "%s (%s, currently %s₫/%s)".formatted(s.name(), s.cancelReminderOn(),
                            vnd(s.amount()), s.cycle().name().toLowerCase(Locale.ROOT)))
                    .collect(Collectors.joining("; ")));
        }
        section(sb, "Money this week (spent %s₫, typical week ~%s₫)"
                .formatted(vnd(spent), vnd(typical)), lines);
    }

    /** VND with Vietnamese thousands dots — "1190000" reads as "1.190.000". */
    private static String vnd(long amount) {
        return NumberFormat.getIntegerInstance(Locale.of("vi", "VN")).format(amount);
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
