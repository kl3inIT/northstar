package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of one vocabulary review. Carries the model before and
 * after plus the fields an FSRS-style optimizer would need (rating, elapsed
 * time) — algorithm-migration insurance: a future scheduler can retrain from
 * this history even though the live model is Ebisu.
 */
@Entity
@Table(name = "vocab_review_log")
public class VocabReviewLog extends BaseEntity {

    /** How confidently the user recalled — the model updates on >= 0.5 as success. */
    public enum Rating { AGAIN, HARD, GOOD, EASY }

    /** Which surface delivered the review. */
    public enum ReviewSource { BRIEF, CHAT, MANUAL }

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(nullable = false)
    private double success;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Rating rating;

    @Column(name = "elapsed_hours", nullable = false)
    private double elapsedHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewSource source;

    @Column(name = "alpha_before", nullable = false)
    private double alphaBefore;

    @Column(name = "beta_before", nullable = false)
    private double betaBefore;

    @Column(name = "halflife_before", nullable = false)
    private double halflifeBefore;

    @Column(name = "alpha_after", nullable = false)
    private double alphaAfter;

    @Column(name = "beta_after", nullable = false)
    private double betaAfter;

    @Column(name = "halflife_after", nullable = false)
    private double halflifeAfter;

    protected VocabReviewLog() {
        // for JPA
    }

    public VocabReviewLog(UUID id, UUID cardId, Instant reviewedAt, double success, Rating rating,
            double elapsedHours, ReviewSource source, double alphaBefore, double betaBefore,
            double halflifeBefore, double alphaAfter, double betaAfter, double halflifeAfter) {
        super(id);
        this.cardId = cardId;
        this.reviewedAt = reviewedAt;
        this.success = success;
        this.rating = rating;
        this.elapsedHours = elapsedHours;
        this.source = source;
        this.alphaBefore = alphaBefore;
        this.betaBefore = betaBefore;
        this.halflifeBefore = halflifeBefore;
        this.alphaAfter = alphaAfter;
        this.betaAfter = betaAfter;
        this.halflifeAfter = halflifeAfter;
    }

    public UUID getCardId() {
        return cardId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public double getSuccess() {
        return success;
    }

    public Rating getRating() {
        return rating;
    }

    public double getElapsedHours() {
        return elapsedHours;
    }

    public ReviewSource getSource() {
        return source;
    }
}
