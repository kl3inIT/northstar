package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable optimizer-ready record of one direction-specific FSRS review. */
@Entity
@Table(name = "vocab_review_log")
public class VocabReviewLog extends BaseEntity {

    public enum Rating {
        AGAIN(0.0), HARD(0.6), GOOD(0.9), EASY(1.0);

        private final double compatibilitySuccess;

        Rating(double compatibilitySuccess) {
            this.compatibilitySuccess = compatibilitySuccess;
        }

        /** Legacy chat/tool confidence field; FSRS scheduling uses the rating itself. */
        public double success() {
            return compatibilitySuccess;
        }
    }

    public enum ReviewSource { BRIEF, CHAT, MANUAL }

    @Column(name = "scheduling_card_id", nullable = false)
    private UUID schedulingCardId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VocabReviewDirection direction;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Rating rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewSource source;

    @Column(name = "elapsed_days", nullable = false)
    private double elapsedDays;

    @Column(nullable = false)
    private boolean lapse;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_before", nullable = false, length = 16)
    private VocabSchedulingState stateBefore;

    @Column(name = "step_before")
    private Integer stepBefore;

    @Column(name = "stability_before")
    private Double stabilityBefore;

    @Column(name = "difficulty_before")
    private Double difficultyBefore;

    @Column(name = "due_before", nullable = false)
    private Instant dueBefore;

    @Column(name = "last_review_before")
    private Instant lastReviewBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_after", nullable = false, length = 16)
    private VocabSchedulingState stateAfter;

    @Column(name = "step_after")
    private Integer stepAfter;

    @Column(name = "stability_after", nullable = false)
    private double stabilityAfter;

    @Column(name = "difficulty_after", nullable = false)
    private double difficultyAfter;

    @Column(name = "due_after", nullable = false)
    private Instant dueAfter;

    @Column(name = "last_review_after", nullable = false)
    private Instant lastReviewAfter;

    protected VocabReviewLog() {
        // for JPA
    }

    VocabReviewLog(UUID id, VocabSchedulingCard before, Instant reviewedAt, Rating rating,
            ReviewSource source, double elapsedDays, boolean lapse,
            VocabScheduler.Outcome after) {
        super(id);
        this.schedulingCardId = before.getId();
        this.cardId = before.getVocabCardId();
        this.direction = before.getDirection();
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt is required");
        this.rating = Objects.requireNonNull(rating, "rating is required");
        this.source = Objects.requireNonNull(source, "source is required");
        this.elapsedDays = elapsedDays;
        this.lapse = lapse;
        this.stateBefore = before.getState();
        this.stepBefore = before.getLearningStep();
        this.stabilityBefore = before.getStabilityDays();
        this.difficultyBefore = before.getDifficulty();
        this.dueBefore = before.getDueAt();
        this.lastReviewBefore = before.getLastReviewedAt();
        this.stateAfter = after.state();
        this.stepAfter = after.learningStep();
        this.stabilityAfter = Objects.requireNonNull(after.stabilityDays());
        this.difficultyAfter = Objects.requireNonNull(after.difficulty());
        this.dueAfter = after.dueAt();
        this.lastReviewAfter = after.reviewedAt();
    }

    public UUID getSchedulingCardId() {
        return schedulingCardId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public VocabReviewDirection getDirection() {
        return direction;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Rating getRating() {
        return rating;
    }

    public ReviewSource getSource() {
        return source;
    }

    public double getElapsedDays() {
        return elapsedDays;
    }

    public boolean isLapse() {
        return lapse;
    }

    public VocabSchedulingState getStateBefore() {
        return stateBefore;
    }

    public VocabSchedulingState getStateAfter() {
        return stateAfter;
    }
}
