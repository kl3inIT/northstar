package com.northstar.core.study;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writing-feedback history — the persistence half of the writing tutor.
 * Grading itself (the LLM call) lives in {@link WritingGrader}, which only the
 * api app wires: this split keeps the history readable from every app (mcp has
 * no LLM) while grading stays where a ChatClient exists.
 */
@Service
public class WritingService {

    private final WritingFeedbackRepository feedbacks;

    WritingService(WritingFeedbackRepository feedbacks) {
        this.feedbacks = feedbacks;
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

    @Transactional
    WritingFeedbackSummary save(WritingFeedback feedback) {
        return WritingFeedbackSummary.of(feedbacks.save(feedback));
    }
}
