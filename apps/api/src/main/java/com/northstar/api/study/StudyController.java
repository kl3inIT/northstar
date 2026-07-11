package com.northstar.api.study;

import com.northstar.core.study.NewStudySession;
import com.northstar.core.study.NewVocabCard;
import com.northstar.core.study.StudyService;
import com.northstar.core.study.StudySessionSummary;
import com.northstar.core.study.StudySource;
import com.northstar.core.study.StudySummary;
import com.northstar.core.study.VocabCardSummary;
import com.northstar.core.study.VocabService;
import com.northstar.core.study.WritingFeedbackSummary;
import com.northstar.core.study.WritingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST delivery for the study log. Reads are date-window-scoped; "this week"
 * depends on the browser zone, so defaults come from the {@code X-Timezone}
 * header like the finance endpoints. Writes are the confirmed-capture batch
 * (CAPTURE source) and per-row corrections; free-text parsing never reaches
 * this controller.
 */
@RestController
@RequestMapping("/api/study")
class StudyController {

    private final StudyService study;
    private final VocabService vocab;
    private final WritingService writing;

    StudyController(StudyService study, VocabService vocab, WritingService writing) {
        this.study = study;
        this.vocab = vocab;
        this.writing = writing;
    }

    /** The log for a window; defaults to the last 30 days ending today. */
    @GetMapping("/sessions")
    @Operation(operationId = "listStudySessions")
    List<StudySessionSummary> sessions(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        LocalDate end = parseDate("to", to, LocalDate.now(zone(tz)));
        LocalDate start = parseDate("from", from, end.minusDays(29));
        return study.sessions(start, end);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "recordStudySessions")
    List<StudySessionSummary> record(
            @Valid @RequestBody StudyRequest.RecordStudySessionsRequest request) {
        List<NewStudySession> items = request.items().stream()
                .map(StudyController::toNewSession)
                .toList();
        return study.recordAll(items, StudySource.CAPTURE);
    }

    @PutMapping("/sessions/{id}")
    @Operation(operationId = "updateStudySession")
    StudySessionSummary update(@PathVariable("id") UUID id,
            @Valid @RequestBody StudyRequest.StudyItemRequest request) {
        return study.update(id, toNewSession(request));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteStudySession")
    void delete(@PathVariable("id") UUID id) {
        study.delete(id);
    }

    /** This week vs last — the page's stat strip and the review's numbers. */
    @GetMapping("/summary")
    @Operation(operationId = "getStudySummary")
    StudySummary summary(
            @RequestParam(name = "reference", required = false) String reference,
            @RequestHeader(name = "X-Timezone", required = false) String tz) {
        return study.summary(parseDate("reference", reference, LocalDate.now(zone(tz))));
    }

    /** Scored mock tests oldest-first — the progress trend. */
    @GetMapping("/mocks")
    @Operation(operationId = "listMockResults")
    List<StudySessionSummary> mocks() {
        return study.mocks();
    }

    /** The constrained skill vocabulary (seed ∪ used) — the row editor's select options. */
    @GetMapping("/skills")
    @Operation(operationId = "listStudySkills")
    List<String> skills() {
        return study.skills();
    }

    /** Every card, newest first, with recall probability computed for now. */
    @GetMapping("/vocab")
    @Operation(operationId = "listVocabCards")
    List<VocabCardSummary> vocabCards() {
        return vocab.cards();
    }

    @PostMapping("/vocab")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "recordVocabCards")
    List<VocabCardSummary> recordVocabCards(
            @Valid @RequestBody StudyRequest.RecordVocabCardsRequest request) {
        List<NewVocabCard> items = request.items().stream()
                .map(i -> new NewVocabCard(i.front(), i.back(), i.metadata(), i.disciplineId()))
                .toList();
        return vocab.createAll(items);
    }

    @PutMapping("/vocab/{id}")
    @Operation(operationId = "updateVocabCard")
    VocabCardSummary updateVocabCard(@PathVariable("id") UUID id,
            @Valid @RequestBody StudyRequest.UpdateVocabCardRequest request) {
        return vocab.update(id, request.front(), request.back(), request.metadata(),
                request.disciplineId(), request.suspended());
    }

    @DeleteMapping("/vocab/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteVocabCard")
    void deleteVocabCard(@PathVariable("id") UUID id) {
        vocab.delete(id);
    }

    /** Every graded essay, newest first. Grading happens in chat, never here. */
    @GetMapping("/writing")
    @Operation(operationId = "listWritingFeedback")
    List<WritingFeedbackSummary> writingFeedback() {
        return writing.list();
    }

    @DeleteMapping("/writing/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteWritingFeedback")
    void deleteWritingFeedback(@PathVariable("id") UUID id) {
        writing.delete(id);
    }

    private static NewStudySession toNewSession(StudyRequest.StudyItemRequest item) {
        return new NewStudySession(item.occurredOn(), item.skill(), item.kind(),
                item.durationMinutes(), item.scoreRaw(), item.scoreMax(), item.notes(),
                item.disciplineId());
    }

    private static LocalDate parseDate(String field, String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(field + " must be yyyy-MM-dd, got '" + value + "'");
        }
    }

    private static ZoneId zone(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(tz.strip());
        } catch (DateTimeException e) {
            return ZoneId.systemDefault();
        }
    }
}
