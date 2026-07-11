package com.northstar.core.assistant;

import static com.northstar.core.assistant.ToolSupport.disciplineIdByName;
import static com.northstar.core.assistant.ToolSupport.parseDate;
import static com.northstar.core.assistant.ToolSupport.zone;

import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.study.NewStudySession;
import com.northstar.core.study.StudyKind;
import com.northstar.core.study.StudyService;
import com.northstar.core.study.StudySessionSummary;
import com.northstar.core.study.StudySource;
import com.northstar.core.study.StudySummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Study-log tools — thin adapters over the study module's public API. */
@Component
class StudyTools implements NorthstarTool {

    private static final String LOG_SESSIONS = """
            Record studying the user ALREADY did into the study log ("làm \
            listening HSK4 25p đúng 18/25", "viết task 2 mất 40 phút"). Accepts \
            a LIST: when one message reports several activities, EVERY activity \
            becomes its own item — never merge two, never drop one. An intention \
            to study later is a task (create_task), not a log entry. skill comes \
            from the study vocabulary — Listening, Reading, Writing, Speaking, \
            Vocabulary, Grammar, Other, plus any skill already visible in \
            study_summary output. kind is MOCK only for a full practice/mock \
            test ("thi thử", "làm đề Cam 18 test 2"); otherwise PRACTICE. Split \
            a stated result into scoreRaw/scoreMax (18/25 -> 18 and 25); never \
            invent a duration or score the user did not state. Resolve relative \
            dates against today to yyyy-MM-dd; pass "" for today. After the \
            call, echo each saved item (skill · duration/score · date) back in \
            one line each.""";

    private static final String FIND_SESSIONS = """
            Recent study-log entries, newest first, over the last N days. Use to \
            answer "tuần này học gì rồi" and to resolve which entry the user \
            means before update_study_session or delete_study_session — results \
            carry the ids those tools need.""";

    private static final String UPDATE_SESSION = """
            Fix one study-log entry by UUID (ids come from find_study_sessions): \
            wrong skill, duration, score, date, or notes. Pass EVERY field with \
            its intended final value (read the entry first) — this is a full \
            replace, not a patch; source never changes.""";

    private static final String DELETE_SESSION = """
            Permanently delete one study-log entry by UUID (from \
            find_study_sessions). No undo — only call on explicit user intent, \
            and name the entry you deleted in your reply.""";

    private static final String SUMMARY = """
            One ISO week of study effort: total minutes, session count, \
            per-skill minutes largest-first, and the previous week's total for \
            comparison. A descriptive reference, not a quota — never scold. Use \
            for "tuần này học bao nhiêu", weekly reviews, and to see which \
            skills the log already uses. Sessions logged without a duration \
            count toward sessionCount but add zero minutes.""";

    private static final String MOCKS = """
            Every mock-test entry (kind=MOCK) oldest first with scores — the \
            progress trend toward the exam. Use for "điểm mock đang lên hay \
            xuống" and before advising what to practice next.""";

    /**
     * One entry of a log_study_sessions call. Strings where the model writes
     * text ({@code kind} PRACTICE|MOCK or "" = PRACTICE, {@code occurredOn}
     * yyyy-MM-dd or "" = today); numbers are already-split integers, null when
     * the user did not state them.
     */
    record StudyItem(String skill, String kind, Integer durationMinutes, Integer scoreRaw,
            Integer scoreMax, String sessionNotes, String occurredOn, String disciplineName) {
    }

    private final StudyService study;
    private final DisciplineService disciplines;

    StudyTools(StudyService study, DisciplineService disciplines) {
        this.study = study;
        this.disciplines = disciplines;
    }

    @Tool(name = "log_study_sessions", description = LOG_SESSIONS)
    @McpTool(name = "log_study_sessions", description = LOG_SESSIONS,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, openWorldHint = false))
    List<StudySessionSummary> logStudySessions(
            @ToolParam(description = "The entries to record — one per activity in the user's message")
            @McpToolParam(description = "The entries to record — one per activity in the user's message",
                    required = true) List<StudyItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one entry");
        }
        List<NewStudySession> resolved = items.stream().map(this::toNewSession).toList();
        return study.recordAll(resolved, StudySource.ASSISTANT);
    }

    @Tool(name = "find_study_sessions", description = FIND_SESSIONS)
    @McpTool(name = "find_study_sessions", description = FIND_SESSIONS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<StudySessionSummary> findStudySessions(
            @ToolParam(description = "Days back to look, 1-90; defaults to 14", required = false)
            @McpToolParam(description = "Days back to look, 1-90; defaults to 14",
                    required = false) Integer daysBack) {
        LocalDate today = LocalDate.now(zone());
        int days = Math.clamp(daysBack == null ? 14 : daysBack, 1, 90);
        return study.sessions(today.minusDays(days), today);
    }

    @Tool(name = "update_study_session", description = UPDATE_SESSION)
    @McpTool(name = "update_study_session", description = UPDATE_SESSION,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    StudySessionSummary updateStudySession(
            @ToolParam(description = "The study session's UUID")
            @McpToolParam(description = "The study session's UUID", required = true) String sessionId,
            @ToolParam(description = "The full replacement entry — every field at its final value")
            @McpToolParam(description = "The full replacement entry — every field at its final value",
                    required = true) StudyItem item) {
        return study.update(UUID.fromString(sessionId), toNewSession(item));
    }

    @Tool(name = "delete_study_session", description = DELETE_SESSION)
    @McpTool(name = "delete_study_session", description = DELETE_SESSION,
            annotations = @McpTool.McpAnnotations(destructiveHint = true, openWorldHint = false))
    String deleteStudySession(
            @ToolParam(description = "The study session's UUID")
            @McpToolParam(description = "The study session's UUID", required = true) String sessionId) {
        UUID id = UUID.fromString(sessionId);
        StudySessionSummary victim = study.find(id);
        study.delete(id);
        return "Deleted study session: " + victim.skill() + " on " + victim.occurredOn();
    }

    @Tool(name = "study_summary", description = SUMMARY)
    @McpTool(name = "study_summary", description = SUMMARY,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    StudySummary studySummary(
            @ToolParam(description = "Any date inside the week to summarize, yyyy-MM-dd; omit for the current week", required = false)
            @McpToolParam(description = "Any date inside the week to summarize, yyyy-MM-dd; omit for the current week",
                    required = false) String reference) {
        LocalDate date = parseDate("reference", reference);
        return study.summary(date == null ? LocalDate.now(zone()) : date);
    }

    @Tool(name = "list_mock_results", description = MOCKS)
    @McpTool(name = "list_mock_results", description = MOCKS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    List<StudySessionSummary> listMockResults() {
        return study.mocks();
    }

    private NewStudySession toNewSession(StudyItem item) {
        StudyKind kind = item.kind() == null || item.kind().isBlank()
                ? StudyKind.PRACTICE
                : switch (item.kind().strip().toUpperCase(Locale.ROOT)) {
                    case "PRACTICE" -> StudyKind.PRACTICE;
                    case "MOCK" -> StudyKind.MOCK;
                    default -> throw new IllegalArgumentException(
                            "kind must be PRACTICE or MOCK, got '" + item.kind() + "'");
                };
        LocalDate occurredOn = parseDate("occurredOn", item.occurredOn());
        return new NewStudySession(
                occurredOn == null ? LocalDate.now(zone()) : occurredOn,
                item.skill(), kind, item.durationMinutes(), item.scoreRaw(), item.scoreMax(),
                item.sessionNotes(), disciplineIdByName(disciplines, item.disciplineName()));
    }
}
