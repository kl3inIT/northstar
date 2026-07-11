package com.northstar.core.study;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The study module's public API. Writes take already-resolved values (absolute
 * dates, integer minutes/scores) — natural-language parsing lives in capture
 * and the assistant. {@link #skills} is the constrained vocabulary fed into
 * every extraction prompt: a seeded skill taxonomy unioned with whatever the
 * log already uses, matched accent-insensitively, so the LLM reuses labels
 * ("Listening", not "nghe"/"listening comprehension") and week-over-week
 * comparison stays meaningful.
 */
@Service
public class StudyService {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final int MAX_MINUTES = 24 * 60;

    /**
     * Language-exam skills plus the mandated sink. Generic on purpose — a
     * future coding/math discipline logs into the same vocabulary and extends
     * it with its own used values instead of forcing a schema change.
     */
    static final List<String> SKILL_SEED = List.of(
            "Listening", "Reading", "Writing", "Speaking", "Vocabulary", "Grammar", "Other");

    private final StudySessionRepository sessions;

    StudyService(StudySessionRepository sessions) {
        this.sessions = sessions;
    }

    /** Record a batch in one transaction — one multi-item capture is one confirm. */
    @Transactional
    public List<StudySessionSummary> recordAll(List<NewStudySession> items, StudySource source) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one entry");
        }
        List<StudySessionSummary> saved = new ArrayList<>();
        for (NewStudySession item : items) {
            saved.add(record(item, source));
        }
        return saved;
    }

    @Transactional
    public StudySessionSummary record(NewStudySession item, StudySource source) {
        StudySession session = new StudySession(UUID.randomUUID(),
                requireDate(item.occurredOn(), "occurredOn"),
                canonicalSkill(item.skill()),
                item.kind() == null ? StudyKind.PRACTICE : item.kind(),
                validDuration(item.durationMinutes()),
                validScoreRaw(item.scoreRaw(), item.scoreMax()),
                validScoreMax(item.scoreRaw(), item.scoreMax()),
                validNotes(item.notes()),
                item.disciplineId(),
                Objects.requireNonNull(source, "source is required"));
        sessions.save(session);
        return StudySessionSummary.of(session);
    }

    /** Full edit of the user-facing fields; {@code source} never changes. */
    @Transactional
    public StudySessionSummary update(UUID id, NewStudySession item) {
        StudySession session = get(id);
        session.edit(
                requireDate(item.occurredOn(), "occurredOn"),
                canonicalSkill(item.skill()),
                item.kind() == null ? session.getKind() : item.kind(),
                validDuration(item.durationMinutes()),
                validScoreRaw(item.scoreRaw(), item.scoreMax()),
                validScoreMax(item.scoreRaw(), item.scoreMax()),
                validNotes(item.notes()),
                item.disciplineId());
        return StudySessionSummary.of(session);
    }

    @Transactional
    public void delete(UUID id) {
        sessions.delete(get(id));
    }

    @Transactional(readOnly = true)
    public StudySessionSummary find(UUID id) {
        return StudySessionSummary.of(get(id));
    }

    /** The log for a date window, newest first. */
    @Transactional(readOnly = true)
    public List<StudySessionSummary> sessions(LocalDate from, LocalDate to) {
        requireRange(from, to);
        return sessions.findByOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(from, to)
                .stream().map(StudySessionSummary::of).toList();
    }

    /** Scored mock tests oldest-first — the progress-to-target trend. */
    @Transactional(readOnly = true)
    public List<StudySessionSummary> mocks() {
        return sessions.findByKindOrderByOccurredOnAscCreatedAtAsc(StudyKind.MOCK)
                .stream().map(StudySessionSummary::of).toList();
    }

    /**
     * The ISO week containing {@code reference} vs the week before — total and
     * per-skill minutes. Descriptive reference for the page, review, and brief.
     */
    @Transactional(readOnly = true)
    public StudySummary summary(LocalDate reference) {
        LocalDate weekStart = requireDate(reference, "reference").with(DayOfWeek.MONDAY);
        List<StudySession> week = sessions
                .findByOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
                        weekStart, weekStart.plusDays(6));
        List<StudySession> previous = sessions
                .findByOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
                        weekStart.minusDays(7), weekStart.minusDays(1));
        Map<String, int[]> bySkill = new LinkedHashMap<>();
        int totalMinutes = 0;
        for (StudySession session : week) {
            int minutes = session.getDurationMinutes() == null ? 0 : session.getDurationMinutes();
            totalMinutes += minutes;
            int[] effort = bySkill.computeIfAbsent(session.getSkill(), k -> new int[2]);
            effort[0] += minutes;
            effort[1]++;
        }
        int previousMinutes = previous.stream()
                .mapToInt(s -> s.getDurationMinutes() == null ? 0 : s.getDurationMinutes()).sum();
        List<StudySummary.SkillEffort> efforts = bySkill.entrySet().stream()
                .map(e -> new StudySummary.SkillEffort(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(StudySummary.SkillEffort::minutes).reversed())
                .toList();
        return new StudySummary(weekStart, totalMinutes, week.size(), previousMinutes, efforts);
    }

    /** Seed ∪ used — the constrained skill vocabulary for prompts and pickers. */
    @Transactional(readOnly = true)
    public List<String> skills() {
        Set<String> known = new LinkedHashSet<>(SKILL_SEED);
        for (String used : sessions.distinctSkills()) {
            if (known.stream().noneMatch(k -> skillKey(k).equals(skillKey(used)))) {
                known.add(used);
            }
        }
        return List.copyOf(known);
    }

    private StudySession get(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new StudySessionNotFoundException(id));
    }

    private String canonicalSkill(String skill) {
        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException("skill is required");
        }
        String normalized = skill.strip();
        if (normalized.codePointCount(0, normalized.length()) > 64) {
            throw new IllegalArgumentException("skill must be at most 64 characters");
        }
        String key = skillKey(normalized);
        return skills().stream()
                .filter(known -> skillKey(known).equals(key))
                .findFirst()
                .orElse(normalized);
    }

    private static String skillKey(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static Integer validDuration(Integer minutes) {
        if (minutes == null) {
            return null;
        }
        if (minutes <= 0 || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException(
                    "durationMinutes must be between 1 and " + MAX_MINUTES);
        }
        return minutes;
    }

    private static Integer validScoreRaw(Integer raw, Integer max) {
        requireScorePair(raw, max);
        return raw;
    }

    private static Integer validScoreMax(Integer raw, Integer max) {
        requireScorePair(raw, max);
        return max;
    }

    private static void requireScorePair(Integer raw, Integer max) {
        if (raw == null && max == null) {
            return;
        }
        if (raw == null || max == null) {
            throw new IllegalArgumentException("scoreRaw and scoreMax come together — "
                    + "pass both (18 of 25) or neither");
        }
        if (max <= 0 || raw < 0 || raw > max) {
            throw new IllegalArgumentException(
                    "score must satisfy 0 <= scoreRaw <= scoreMax with scoreMax > 0");
        }
    }

    private static String validNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String stripped = notes.strip();
        if (stripped.codePointCount(0, stripped.length()) > 2000) {
            throw new IllegalArgumentException("notes must be at most 2000 characters");
        }
        return stripped;
    }

    private static LocalDate requireDate(LocalDate value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        requireDate(from, "from");
        requireDate(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
    }
}
