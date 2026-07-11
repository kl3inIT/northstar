package com.northstar.core.study;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Structured output of one grading call. {@code reasoning} comes FIRST so the
 * model must work through the essay before committing to bands
 * (reason-then-label, the capture-classifier precedent). The overall band is
 * a RANGE — a single number would claim examiner precision an LLM does not
 * have; the verified evidence says LLM estimates are reliable to about a
 * half-band, so the range spans that uncertainty.
 */
public record WritingGrade(
        String reasoning,
        @NotNull List<CriterionGrade> criteria,
        double overallMin,
        double overallMax,
        String summary,
        @NotNull List<EssayError> topErrors) {

    /**
     * One rubric criterion. {@code key} is the rubric's short code (TR/TA, CC,
     * LR, GRA); {@code justification} must quote evidence from the essay — a
     * band without evidence is the failure mode the rubric forbids.
     */
    public record CriterionGrade(String key, double band, String justification) {
    }

    /**
     * One recurring error pattern: what it is, a verbatim {@code quote} from
     * the essay showing it, and the corrected version. These accumulate into
     * the learner error corpus the next grading compares against.
     */
    public record EssayError(String label, String quote, String fix) {
    }
}
