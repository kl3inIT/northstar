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

    private static final String DAILY_SYSTEM = """
            Bạn là bạn đồng hành tổng kết cuối ngày trong Northstar (personal-growth OS).
            Người dùng KHÔNG muốn tự viết journal — bạn viết nháp, họ chỉ đọc.
            Tin nhắn của người dùng là bảng số liệu thật trong ngày. Viết 3–5 câu
            tiếng Việt, giọng thẳng thắn, không tâng bốc, không sáo rỗng:
            - Nhận xét trung thực về ngày hôm nay (xong gì đáng kể, trượt gì).
            - Nếu có task quá hạn nhiều ngày, gọi thẳng tên nó.
            - Nếu có note đang chờ duyệt (Staging), nhắc bằng một câu.
            - Kết bằng đúng một dòng: **Mai ưu tiên:** <một việc duy nhất, chọn từ số liệu>.
            Không dùng heading, không chép lại bảng số liệu, không bịa ra việc không có.
            """;

    private static final String WEEKLY_SYSTEM = """
            Bạn là bạn đồng hành tổng kết cuối tuần trong Northstar (personal-growth OS).
            Người dùng KHÔNG muốn tự viết journal — bạn viết nháp, họ chỉ đọc.
            Tin nhắn của người dùng là bảng số liệu thật của tuần. Viết 5–7 câu
            tiếng Việt, giọng thẳng thắn, không tâng bốc, không sáo rỗng:
            - Tuần này được gì, trượt gì; nêu pattern nếu thấy (task nào lặp lại
              việc trễ hạn, việc dồn về cuối tuần).
            - Nếu có note đang chờ duyệt (Staging), nhắc bằng một câu.
            - Kết bằng đúng một dòng: **Tuần tới ưu tiên:** <một việc duy nhất, chọn từ số liệu>.
            Không dùng heading, không chép lại bảng số liệu, không bịa ra việc không có.
            """;

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

    /** (Re)drafts today's review — one LLM call — and upserts the Journal note. */
    public NoteDetail generateDaily(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        String facts = dailyFacts(today, zone);
        String body = commentary(DAILY_SYSTEM, facts) + "\n\n---\n\n" + facts;
        return upsert(dailyTitle(today), List.of("alignment", "daily"), body);
    }

    /** (Re)drafts this week's review — one LLM call — and upserts the Journal note. */
    public NoteDetail generateWeekly(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        String facts = weeklyFacts(today, zone);
        String body = commentary(WEEKLY_SYSTEM, facts) + "\n\n---\n\n" + facts;
        return upsert(weeklyTitle(today), List.of("alignment", "weekly"), body);
    }

    // --- internals ---------------------------------------------------------

    private String dailyTitle(LocalDate day) {
        return "Tổng kết ngày " + day;
    }

    private String weeklyTitle(LocalDate day) {
        WeekFields iso = WeekFields.ISO;
        return "Tổng kết tuần %d-W%02d".formatted(
                day.get(iso.weekBasedYear()), day.get(iso.weekOfWeekBasedYear()));
    }

    /** The AI paragraph; on any LLM failure the note still ships with facts only. */
    private String commentary(String system, String facts) {
        try {
            String text = chat.prompt().system(system).user(facts).call().content();
            if (text != null && !text.isBlank()) {
                return text.strip();
            }
        } catch (RuntimeException e) {
            log.warn("Alignment commentary failed; falling back to facts-only note", e);
        }
        return "*Không tạo được nhận xét AI lần này — dưới đây là số liệu thuần.*";
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

        StringBuilder sb = new StringBuilder("## Số liệu\n");
        section(sb, "Xong hôm nay (%d)".formatted(done.size()),
                done.stream().map(t -> "- " + t.title()).toList());
        section(sb, "Còn mở (%d, quá hạn %d)".formatted(overdue.size() + dueToday.size(), overdue.size()),
                java.util.stream.Stream.concat(
                        overdue.stream().map(t -> "- %s — quá hạn %d ngày"
                                .formatted(t.title(), ChronoUnit.DAYS.between(t.dueDate(), today))),
                        dueToday.stream().map(t -> "- %s — hạn hôm nay%s"
                                .formatted(t.title(), t.dueTime() == null ? "" : " " + TIME.format(t.dueTime()))))
                        .toList());
        section(sb, "Note chờ duyệt / Staging (%d)".formatted(staging.size()),
                staging.stream().map(n -> "- " + n.title()).toList());
        section(sb, "Ngày mai (%d task, %d event)".formatted(tomorrow.size(), tomorrowEvents.size()),
                java.util.stream.Stream.concat(
                        tomorrow.stream().map(t -> "- Task: %s%s"
                                .formatted(t.title(), t.dueTime() == null ? "" : " (" + TIME.format(t.dueTime()) + ")")),
                        tomorrowEvents.stream().map(e -> "- " + eventLine(e, zone)))
                        .toList());
        return sb.toString().stripTrailing();
    }

    private String weeklyFacts(LocalDate today, ZoneId zone) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<TaskSummary> week = tasks.range(monday, monday.plusDays(6));
        List<TaskSummary> done = week.stream().filter(t -> t.status() == TaskStatus.DONE).toList();
        List<TaskSummary> open = week.stream().filter(t -> t.status() == TaskStatus.OPEN).toList();
        List<TaskSummary> nextWeek = tasks.range(monday.plusDays(7), monday.plusDays(13)).stream()
                .filter(t -> t.status() == TaskStatus.OPEN).toList();
        List<NoteSummary> staging = stagingNotes();

        StringBuilder sb = new StringBuilder("## Số liệu tuần %s → %s\n".formatted(monday, monday.plusDays(6)));
        section(sb, "Xong trong tuần (%d)".formatted(done.size()),
                done.stream().map(t -> "- %s (hạn %s)".formatted(t.title(), t.dueDate())).toList());
        section(sb, "Chưa xong (%d)".formatted(open.size()),
                open.stream().map(t -> "- %s (hạn %s)".formatted(t.title(), t.dueDate())).toList());
        section(sb, "Note chờ duyệt / Staging (%d)".formatted(staging.size()),
                staging.stream().map(n -> "- " + n.title()).toList());
        section(sb, "Tuần tới (%d task có hạn)".formatted(nextWeek.size()),
                nextWeek.stream().map(t -> "- %s (hạn %s)".formatted(t.title(), t.dueDate())).toList());
        return sb.toString().stripTrailing();
    }

    private List<NoteSummary> stagingNotes() {
        return notes.listByStatus(NoteStatus.STAGING,
                        PageRequest.of(0, MAX_STAGING_LINES, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    private String eventLine(CalendarEventSummary event, ZoneId zone) {
        if (event.allDay()) {
            return "Cả ngày: " + event.title();
        }
        LocalDateTime start = LocalDateTime.ofInstant(event.startAt(), zone);
        LocalDateTime end = LocalDateTime.ofInstant(event.endAt(), zone);
        return "%s–%s %s".formatted(TIME.format(start), TIME.format(end), event.title());
    }

    private static void section(StringBuilder sb, String heading, List<String> lines) {
        sb.append("\n**").append(heading).append(":**\n");
        if (lines.isEmpty()) {
            sb.append("- (không có)\n");
        } else {
            lines.forEach(line -> sb.append(line).append('\n'));
        }
    }
}
