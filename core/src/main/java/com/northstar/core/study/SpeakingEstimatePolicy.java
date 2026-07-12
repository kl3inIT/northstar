package com.northstar.core.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure validation and deterministic aggregation for one-answer IELTS-style estimates. */
final class SpeakingEstimatePolicy {

    static final String VERSION = "ielts-speaking-one-answer-v1";
    static final String LABEL = "Unofficial one-answer IELTS-style estimate";
    private static final List<String> REQUIRED_KEYS = List.of("FC", "LR", "GRA", "P");
    private static final Set<String> CONFIDENCE = Set.of("LOW", "MEDIUM");

    private SpeakingEstimatePolicy() {
    }

    static String problems(List<SpeakingIeltsEstimate.Criterion> criteria, String transcript) {
        if (criteria == null || criteria.isEmpty()) return "No IELTS-style criteria returned.";
        StringBuilder problems = new StringBuilder();
        Map<String, SpeakingIeltsEstimate.Criterion> byKey = new LinkedHashMap<>();
        String normalizedTranscript = normalize(transcript);
        for (SpeakingIeltsEstimate.Criterion criterion : criteria) {
            if (criterion == null) {
                problems.append("- Null IELTS-style criterion.\n");
                continue;
            }
            String key = criterion.key() == null ? "" : criterion.key().strip().toUpperCase(Locale.ROOT);
            if (!REQUIRED_KEYS.contains(key)) {
                problems.append("- Unknown IELTS-style criterion key: ").append(key).append(".\n");
            } else if (byKey.putIfAbsent(key, criterion) != null) {
                problems.append("- Duplicate IELTS-style criterion key: ").append(key).append(".\n");
            }
            if (!validBand(criterion.minBand()) || !validBand(criterion.maxBand())
                    || criterion.minBand() > criterion.maxBand()
                    || criterion.maxBand() - criterion.minBand() > 1.0) {
                problems.append("- Invalid band range for ").append(key).append(": ")
                        .append(criterion.minBand()).append("..").append(criterion.maxBand())
                        .append(".\n");
            }
            String confidence = criterion.confidence() == null ? ""
                    : criterion.confidence().strip().toUpperCase(Locale.ROOT);
            if (!CONFIDENCE.contains(confidence)) {
                problems.append("- Confidence for ").append(key)
                        .append(" must be LOW or MEDIUM for one answer.\n");
            }
            if (criterion.justification() == null || criterion.justification().isBlank()) {
                problems.append("- Justification for ").append(key).append(" is empty.\n");
            }
            if (criterion.evidenceQuote() == null || criterion.evidenceQuote().isBlank()
                    || !normalizedTranscript.contains(normalize(criterion.evidenceQuote()))) {
                problems.append("- Evidence quote for ").append(key)
                        .append(" is not verbatim from the transcript: \"")
                        .append(criterion.evidenceQuote()).append("\".\n");
            }
        }
        for (String key : REQUIRED_KEYS) {
            if (!byKey.containsKey(key)) problems.append("- Missing IELTS-style criterion: ").append(key).append(".\n");
        }
        return problems.isEmpty() ? null : problems.toString().strip();
    }

    static SpeakingIeltsEstimate aggregate(List<SpeakingIeltsEstimate.Criterion> criteria) {
        Map<String, SpeakingIeltsEstimate.Criterion> byKey = new LinkedHashMap<>();
        for (SpeakingIeltsEstimate.Criterion criterion : criteria) {
            byKey.put(criterion.key().strip().toUpperCase(Locale.ROOT), criterion);
        }
        if (!byKey.keySet().containsAll(REQUIRED_KEYS) || byKey.size() != REQUIRED_KEYS.size()) {
            throw new IllegalArgumentException("Exactly FC, LR, GRA, and P are required");
        }
        List<SpeakingIeltsEstimate.Criterion> ordered = REQUIRED_KEYS.stream().map(byKey::get).toList();
        double overallMin = halfBand(ordered.stream().mapToDouble(SpeakingIeltsEstimate.Criterion::minBand)
                .average().orElseThrow());
        double overallMax = halfBand(ordered.stream().mapToDouble(SpeakingIeltsEstimate.Criterion::maxBand)
                .average().orElseThrow());
        return new SpeakingIeltsEstimate(ordered, overallMin, overallMax, "LOW", LABEL);
    }

    private static boolean validBand(double band) {
        return Double.isFinite(band) && band >= 1.0 && band <= 9.0
                && band * 2 == Math.rint(band * 2);
    }

    private static double halfBand(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }
}
