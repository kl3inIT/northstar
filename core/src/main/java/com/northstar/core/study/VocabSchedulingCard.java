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

/** One independently scheduled recognition or production direction. */
@Entity
@Table(name = "vocab_scheduling_card")
class VocabSchedulingCard extends BaseEntity {

    @Column(name = "vocab_card_id", nullable = false)
    private UUID vocabCardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VocabReviewDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VocabSchedulingState state;

    @Column(name = "learning_step")
    private Integer learningStep;

    @Column(name = "stability_days")
    private Double stabilityDays;

    private Double difficulty;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "lapse_count", nullable = false)
    private int lapseCount;

    @Column(nullable = false)
    private boolean leech;

    @Column(name = "buried_until")
    private Instant buriedUntil;

    protected VocabSchedulingCard() {
        // for JPA
    }

    VocabSchedulingCard(UUID id, UUID vocabCardId, VocabReviewDirection direction, Instant dueAt) {
        super(id);
        this.vocabCardId = Objects.requireNonNull(vocabCardId, "vocabCardId is required");
        this.direction = Objects.requireNonNull(direction, "direction is required");
        this.state = VocabSchedulingState.LEARNING;
        this.learningStep = 0;
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt is required");
    }

    UUID getVocabCardId() {
        return vocabCardId;
    }

    VocabReviewDirection getDirection() {
        return direction;
    }

    VocabSchedulingState getState() {
        return state;
    }

    Integer getLearningStep() {
        return learningStep;
    }

    Double getStabilityDays() {
        return stabilityDays;
    }

    Double getDifficulty() {
        return difficulty;
    }

    Instant getDueAt() {
        return dueAt;
    }

    Instant getLastReviewedAt() {
        return lastReviewedAt;
    }

    int getLapseCount() {
        return lapseCount;
    }

    boolean isLeech() {
        return leech;
    }

    Instant getBuriedUntil() {
        return buriedUntil;
    }

    boolean isDue(Instant now) {
        return !dueAt.isAfter(now) && (buriedUntil == null || !buriedUntil.isAfter(now));
    }

    void apply(VocabScheduler.Outcome outcome, boolean lapse) {
        state = outcome.state();
        learningStep = outcome.learningStep();
        stabilityDays = outcome.stabilityDays();
        difficulty = outcome.difficulty();
        dueAt = outcome.dueAt();
        lastReviewedAt = outcome.reviewedAt();
        buriedUntil = null;
        if (lapse) {
            lapseCount++;
            leech = lapseCount >= VocabScheduler.LEECH_THRESHOLD;
        }
    }

    void buryUntil(Instant boundary) {
        buriedUntil = Objects.requireNonNull(boundary, "boundary is required");
    }
}

