package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/**
 * One vocabulary card plus its Ebisu memory model. {@code front} is the
 * prompt side (the word), {@code back} the answer (meaning); language
 * specifics — pinyin, example sentences, audio — travel in {@code metadata}
 * as a JSON string so new subjects never need schema changes. The model
 * triple ({@code alpha}, {@code beta}, {@code halflifeHours}) is anchored at
 * {@code lastReviewedAt}; there is deliberately NO due date — consumers rank
 * cards by predicted recall instead, so missed days never pile up a backlog.
 */
@Entity
@Table(name = "vocab_card")
public class VocabCard extends BaseEntity {

    @NotBlank
    @Column(nullable = false, length = 255)
    private String front;

    @NotBlank
    @Column(nullable = false, length = 1000)
    private String back;

    @Column(length = 4000)
    private String metadata;

    @Column(name = "discipline_id")
    private UUID disciplineId;

    @Column(nullable = false)
    private double alpha;

    @Column(nullable = false)
    private double beta;

    @Column(name = "halflife_hours", nullable = false)
    private double halflifeHours;

    @Column(name = "last_reviewed_at", nullable = false)
    private Instant lastReviewedAt;

    @Column(nullable = false)
    private boolean suspended;

    protected VocabCard() {
        // for JPA
    }

    public VocabCard(UUID id, String front, String back, String metadata, UUID disciplineId,
            double alpha, double beta, double halflifeHours, Instant lastReviewedAt) {
        super(id);
        this.front = front;
        this.back = back;
        this.metadata = metadata;
        this.disciplineId = disciplineId;
        this.alpha = alpha;
        this.beta = beta;
        this.halflifeHours = halflifeHours;
        this.lastReviewedAt = lastReviewedAt;
        this.suspended = false;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public String getMetadata() {
        return metadata;
    }

    public UUID getDisciplineId() {
        return disciplineId;
    }

    public double getAlpha() {
        return alpha;
    }

    public double getBeta() {
        return beta;
    }

    public double getHalflifeHours() {
        return halflifeHours;
    }

    public Instant getLastReviewedAt() {
        return lastReviewedAt;
    }

    public boolean isSuspended() {
        return suspended;
    }

    /** Edit the content sides; the memory model is only ever moved by a review. */
    public void edit(String front, String back, String metadata, UUID disciplineId,
            boolean suspended) {
        this.front = front;
        this.back = back;
        this.metadata = metadata;
        this.disciplineId = disciplineId;
        this.suspended = suspended;
    }

    /** Fold a review into the memory model — the only writer of the triple. */
    public void reviewed(double alpha, double beta, double halflifeHours, Instant reviewedAt) {
        this.alpha = alpha;
        this.beta = beta;
        this.halflifeHours = halflifeHours;
        this.lastReviewedAt = reviewedAt;
    }
}
