package com.northstar.api.discipline;

import com.northstar.core.calendar.CalendarEventService;
import com.northstar.core.calendar.CalendarEventSummary;
import com.northstar.core.discipline.DisciplineService;
import com.northstar.core.discipline.DisciplineSummary;
import com.northstar.core.note.NoteService;
import com.northstar.core.note.NoteSummary;
import com.northstar.core.task.TaskService;
import com.northstar.core.task.TaskSummary;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST delivery for the discipline module. Besides the picker CRUD, this is
 * where the LDP slice views are COMPOSED: the api app is the one place allowed
 * to fan out across core modules' public APIs (task/calendar/note all FK to a
 * discipline; the discipline module itself must not depend back on them).
 */
@RestController
@RequestMapping("/api/disciplines")
class DisciplineController {

    /** "Upcoming" on cards and the slice = the next 7 days. */
    private static final Duration UPCOMING_WINDOW = Duration.ofDays(7);
    private static final int SLICE_NOTES = 6;

    private final DisciplineService disciplines;
    private final TaskService tasks;
    private final CalendarEventService events;
    private final NoteService notes;

    DisciplineController(DisciplineService disciplines, TaskService tasks,
            CalendarEventService events, NoteService notes) {
        this.disciplines = disciplines;
        this.tasks = tasks;
        this.events = events;
        this.notes = notes;
    }

    @GetMapping
    List<DisciplineSummary> list() {
        return disciplines.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DisciplineSummary create(@Valid @RequestBody DisciplineRequest request) {
        return disciplines.create(request.name(), request.color());
    }

    @PutMapping("/{id}")
    DisciplineSummary update(@PathVariable UUID id, @Valid @RequestBody DisciplineRequest request) {
        return disciplines.update(id, request.name(), request.color());
    }

    /** The /disciplines grid: every discipline with its live counts. */
    @GetMapping("/cards")
    List<DisciplineCard> cards(@RequestHeader(name = "X-Timezone", required = false) String tz) {
        Instant now = Instant.now();
        Map<UUID, Long> eventCounts = events.range(now, now.plus(UPCOMING_WINDOW), zone(tz)).stream()
                .filter(e -> e.disciplineId() != null)
                .collect(Collectors.groupingBy(CalendarEventSummary::disciplineId, Collectors.counting()));
        return disciplines.list().stream()
                .map(d -> new DisciplineCard(d,
                        tasks.openByDiscipline(d.id()).size(),
                        eventCounts.getOrDefault(d.id(), 0L),
                        noteCount(d.name())))
                .toList();
    }

    /** The slice page: everything one discipline holds right now. */
    @GetMapping("/{id}/overview")
    DisciplineOverview overview(@PathVariable UUID id,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        DisciplineSummary discipline = disciplines.find(id);
        List<TaskSummary> openTasks = tasks.openByDiscipline(id);
        Instant now = Instant.now();
        List<CalendarEventSummary> upcoming = events.range(now, now.plus(UPCOMING_WINDOW), zone(tz)).stream()
                .filter(e -> id.equals(e.disciplineId()))
                .toList();
        Page<NoteSummary> tagged = notes.listByAnyTag(nameTokens(discipline.name()),
                PageRequest.of(0, SLICE_NOTES, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return new DisciplineOverview(discipline, openTasks, upcoming,
                tagged.getContent(), tagged.getTotalElements());
    }

    private long noteCount(String disciplineName) {
        return notes.listByAnyTag(nameTokens(disciplineName), PageRequest.of(0, 1)).getTotalElements();
    }

    /**
     * The MFI tag bridge: a note belongs to a discipline when it carries one of
     * the discipline name's words as a tag ("English · IELTS" → english, ielts).
     */
    private static List<String> nameTokens(String disciplineName) {
        return List.of(disciplineName.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .stream().filter(t -> t.length() >= 2).toList();
    }

    private static ZoneId zone(String tz) {
        try {
            return tz == null ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
