package com.northstar.core.study;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.CardAndReviewLog;
import io.github.openspacedrepetition.Scheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Narrow domain boundary around the official FSRS-6 Java library. Library
 * types never leave this class.
 */
final class VocabScheduler {

    static final double DESIRED_RETENTION = 0.90;
    static final int LEECH_THRESHOLD = 8;
    static final int MAXIMUM_INTERVAL_DAYS = 36_500;
    private static final Duration[] LEARNING_STEPS = {
            Duration.ofMinutes(1), Duration.ofMinutes(10)
    };
    private static final Duration[] RELEARNING_STEPS = {Duration.ofMinutes(10)};

    private VocabScheduler() {
    }

    static List<VocabRatingPreview> previews(VocabSchedulingCard card, Instant reviewedAt) {
        return Arrays.stream(VocabReviewLog.Rating.values())
                .map(rating -> {
                    Outcome outcome = schedule(card, rating, reviewedAt);
                    long seconds = Math.max(0, Duration.between(reviewedAt, outcome.dueAt()).toSeconds());
                    return new VocabRatingPreview(rating, outcome.state(), outcome.dueAt(), seconds,
                            intervalLabel(Duration.ofSeconds(seconds)));
                })
                .toList();
    }

    static Outcome schedule(VocabSchedulingCard source, VocabReviewLog.Rating rating,
            Instant reviewedAt) {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(rating, "rating is required");
        Instant at = Objects.requireNonNull(reviewedAt, "reviewedAt is required");
        Card before = toLibraryCard(source);
        CardAndReviewLog result = scheduler(seed(source, at)).reviewCard(before,
                io.github.openspacedrepetition.Rating.valueOf(rating.name()), at);
        Card after = result.card();
        return new Outcome(
                VocabSchedulingState.valueOf(after.getState().name()),
                after.getStep(), after.getStability(), after.getDifficulty(),
                after.getDue(), after.getLastReview());
    }

    static double retrievability(VocabSchedulingCard source, Instant at) {
        if (source.getStabilityDays() == null || source.getLastReviewedAt() == null) {
            return 0.0;
        }
        return scheduler(0).getCardRetrievability(toLibraryCard(source), at);
    }

    static boolean isLapse(VocabSchedulingCard source, VocabReviewLog.Rating rating) {
        return source.getState() == VocabSchedulingState.REVIEW
                && rating == VocabReviewLog.Rating.AGAIN;
    }

    static double elapsedDays(VocabSchedulingCard source, Instant at) {
        if (source.getLastReviewedAt() == null) return 0.0;
        return Math.max(0.0, Duration.between(source.getLastReviewedAt(), at).toSeconds()
                / 86_400.0);
    }

    private static Scheduler scheduler(int seed) {
        return Scheduler.builder()
                .desiredRetention(DESIRED_RETENTION)
                .learningSteps(LEARNING_STEPS.clone())
                .relearningSteps(RELEARNING_STEPS.clone())
                .maximumInterval(MAXIMUM_INTERVAL_DAYS)
                .enableFuzzing(true)
                .randomSeedNumber(seed)
                .build();
    }

    private static Card toLibraryCard(VocabSchedulingCard source) {
        return Card.builder()
                .cardId(source.getId().hashCode())
                .state(io.github.openspacedrepetition.State.valueOf(source.getState().name()))
                .step(source.getLearningStep())
                .stability(source.getStabilityDays())
                .difficulty(source.getDifficulty())
                .due(source.getDueAt())
                .lastReview(source.getLastReviewedAt())
                .build();
    }

    private static int seed(VocabSchedulingCard source, Instant at) {
        int value = 31 * source.getId().hashCode() + Long.hashCode(source.getVersion());
        return 31 * value + Long.hashCode(at.toEpochMilli());
    }

    static String intervalLabel(Duration interval) {
        long seconds = Math.max(0, interval.toSeconds());
        if (seconds < 60) return Math.max(1, seconds) + "s";
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 60) return minutes + "m";
        long hours = Math.round(seconds / 3_600.0);
        if (hours < 24) return hours + "h";
        long days = Math.round(seconds / 86_400.0);
        if (days < 30) return days + "d";
        if (days < 365) return Math.round(days / 30.0) + "mo";
        return Math.round(days / 365.0) + "y";
    }

    record Outcome(
            VocabSchedulingState state,
            Integer learningStep,
            Double stabilityDays,
            Double difficulty,
            Instant dueAt,
            Instant reviewedAt) {
    }
}

