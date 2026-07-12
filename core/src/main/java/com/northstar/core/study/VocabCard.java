package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VocabLanguage language;

    @Column(length = 80)
    private String deck;

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

    @Column(name = "production_enabled", nullable = false)
    private boolean productionEnabled;

    @Column(name = "production_alpha")
    private Double productionAlpha;

    @Column(name = "production_beta")
    private Double productionBeta;

    @Column(name = "production_halflife_hours")
    private Double productionHalflifeHours;

    @Column(name = "production_last_reviewed_at")
    private Instant productionLastReviewedAt;

    protected VocabCard() {
        // for JPA
    }

    public VocabCard(UUID id, String front, String back, String metadata,
            VocabLanguage language, String deck, UUID disciplineId,
            double alpha, double beta, double halflifeHours, Instant lastReviewedAt) {
        super(id);
        this.front = front;
        this.back = back;
        this.metadata = metadata;
        this.language = language;
        this.deck = deck;
        this.disciplineId = disciplineId;
        this.alpha = alpha;
        this.beta = beta;
        this.halflifeHours = halflifeHours;
        this.lastReviewedAt = lastReviewedAt;
        this.suspended = false;
        this.productionEnabled = false;
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

    public VocabLanguage getLanguage() {
        return language;
    }

    public String getDeck() {
        return deck;
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

    public boolean isProductionEnabled() {
        return productionEnabled;
    }

    public Double getProductionAlpha() {
        return productionAlpha;
    }

    public Double getProductionBeta() {
        return productionBeta;
    }

    public Double getProductionHalflifeHours() {
        return productionHalflifeHours;
    }

    public Instant getProductionLastReviewedAt() {
        return productionLastReviewedAt;
    }

    /** Edit the content sides; the memory model is only ever moved by a review. */
    public void edit(String front, String back, String metadata, VocabLanguage language,
            String deck, UUID disciplineId, boolean suspended) {
        edit(front, back, metadata, language, deck, disciplineId, suspended, productionEnabled);
    }

    public void edit(String front, String back, String metadata, VocabLanguage language,
            String deck, UUID disciplineId, boolean suspended, boolean enableProduction) {
        this.front = front;
        this.back = back;
        this.metadata = metadata;
        this.language = language;
        this.deck = deck;
        this.disciplineId = disciplineId;
        this.suspended = suspended;
        setProductionEnabled(enableProduction, Instant.now());
    }

    /** Fold a review into the memory model — the only writer of the triple. */
    public void reviewed(double alpha, double beta, double halflifeHours, Instant reviewedAt) {
        this.alpha = alpha;
        this.beta = beta;
        this.halflifeHours = halflifeHours;
        this.lastReviewedAt = reviewedAt;
    }

    /** Enable/disable the production sibling while retaining learned state on disable. */
    public void setProductionEnabled(boolean enabled, Instant now) {
        productionEnabled = enabled;
        if (enabled && productionAlpha == null) {
            productionAlpha = 2.0;
            productionBeta = 2.0;
            productionHalflifeHours = 24.0;
            productionLastReviewedAt = now;
        }
    }

    public void productionReviewed(double alpha, double beta, double halflifeHours,
            Instant reviewedAt) {
        if (!productionEnabled) {
            throw new IllegalStateException("Production review is not enabled");
        }
        productionAlpha = alpha;
        productionBeta = beta;
        productionHalflifeHours = halflifeHours;
        productionLastReviewedAt = reviewedAt;
    }
}
