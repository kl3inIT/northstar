package com.northstar.core.study;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Writing-feedback history — the persistence half of the writing tutor.
 * Grading itself (the LLM call) lives in {@link WritingGrader}, which only the
 * api app wires: this split keeps the history readable from every app (mcp has
 * no LLM) while grading stays where a ChatClient exists.
 */
@Service
public class WritingService {

    private static final int MAX_WEAKNESSES = 20;
    private static final int MAX_EXAMPLES_PER_WEAKNESS = 3;

    private final WritingFeedbackRepository feedbacks;
    private final ObjectMapper json;

    WritingService(WritingFeedbackRepository feedbacks, ObjectMapper json) {
        this.feedbacks = feedbacks;
        this.json = json;
    }

    /** Every grading, newest first. */
    public List<WritingFeedbackSummary> list() {
        return feedbacks.findByOrderBySubmittedAtDesc().stream()
                .map(WritingFeedbackSummary::of)
                .toList();
    }

    public WritingFeedbackSummary find(UUID id) {
        return feedbacks.findById(id)
                .map(WritingFeedbackSummary::of)
                .orElseThrow(() -> new WritingFeedbackNotFoundException(id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!feedbacks.existsById(id)) {
            throw new WritingFeedbackNotFoundException(id);
        }
        feedbacks.deleteById(id);
    }

    /** Latest gradings for the error-corpus section of the grader prompt. */
    List<WritingFeedback> recentForCorpus() {
        return feedbacks.findTop10ByOrderBySubmittedAtDesc();
    }

    /**
     * The learner's recurring error patterns, aggregated across every grading:
     * grouped case-insensitively by label, most recently seen first, each with
     * up to {@value MAX_EXAMPLES_PER_WEAKNESS} recent quote→fix examples from
     * the user's own essays. Malformed stored JSON is skipped, not fatal — one
     * bad row must not take the grammar tutor down.
     */
    public List<GrammarWeakness> grammarWeaknesses() {
        Map<String, Aggregate> byLabel = new LinkedHashMap<>();
        // Newest first, so the first label casing and examples seen are the freshest.
        for (WritingFeedback feedback : feedbacks.findByOrderBySubmittedAtDesc()) {
            LocalDate seen = feedback.getSubmittedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            for (JsonNode error : parseErrors(feedback.getTopErrors())) {
                String label = error.path("label").asString("").strip();
                if (label.isEmpty()) {
                    continue;
                }
                Aggregate aggregate = byLabel.computeIfAbsent(
                        label.toLowerCase(Locale.ROOT), key -> new Aggregate(label, seen));
                aggregate.occurrences++;
                if (aggregate.examples.size() < MAX_EXAMPLES_PER_WEAKNESS) {
                    aggregate.examples.add(new GrammarWeakness.GrammarExample(
                            error.path("quote").asString(""), error.path("fix").asString("")));
                }
            }
        }
        return byLabel.values().stream()
                .sorted((a, b) -> {
                    int byDate = b.lastSeen.compareTo(a.lastSeen);
                    return byDate != 0 ? byDate : Integer.compare(b.occurrences, a.occurrences);
                })
                .limit(MAX_WEAKNESSES)
                .map(a -> new GrammarWeakness(a.label, a.occurrences, a.lastSeen, List.copyOf(a.examples)))
                .toList();
    }

    private List<JsonNode> parseErrors(String topErrors) {
        try {
            JsonNode parsed = json.readTree(topErrors);
            if (!parsed.isArray()) {
                return List.of();
            }
            List<JsonNode> errors = new ArrayList<>();
            parsed.forEach(errors::add);
            return errors;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static final class Aggregate {
        final String label;
        final LocalDate lastSeen;
        int occurrences;
        final List<GrammarWeakness.GrammarExample> examples = new ArrayList<>();

        Aggregate(String label, LocalDate lastSeen) {
            this.label = label;
            this.lastSeen = lastSeen;
        }
    }

    @Transactional
    WritingFeedbackSummary save(WritingFeedback feedback) {
        return WritingFeedbackSummary.of(feedbacks.save(feedback));
    }
}
