package com.northstar.core.study;

import java.util.List;

/** Auditable, explicitly unofficial estimate derived from one practice answer. */
public record SpeakingIeltsEstimate(
        List<Criterion> criteria,
        double overallMin,
        double overallMax,
        String confidence,
        String label) {

    public SpeakingIeltsEstimate {
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        confidence = confidence == null ? "LOW" : confidence;
        label = label == null ? "" : label;
    }

    /** One IELTS-style criterion range with evidence anchored to the attempt. */
    public record Criterion(
            String key,
            double minBand,
            double maxBand,
            String confidence,
            String evidenceQuote,
            String justification) {
    }
}
