package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * One vocabulary content note. {@code front} is the prompt side (the word),
 * {@code back} the answer (meaning); language
 * specifics — pinyin, example sentences, audio — travel in {@code metadata}
 * as a JSON string so new subjects never need schema changes. Independent
 * recognition/production memory lives in {@link VocabSchedulingCard} rows.
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
    private boolean suspended;

    @Column(name = "production_enabled", nullable = false)
    private boolean productionEnabled;

    protected VocabCard() {
        // for JPA
    }

    public VocabCard(UUID id, String front, String back, String metadata,
            VocabLanguage language, String deck, UUID disciplineId) {
        super(id);
        this.front = front;
        this.back = back;
        this.metadata = metadata;
        this.language = language;
        this.deck = deck;
        this.disciplineId = disciplineId;
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

    public boolean isSuspended() {
        return suspended;
    }

    public boolean isProductionEnabled() {
        return productionEnabled;
    }

    /** Edit the content sides; scheduling state is only ever moved by a review. */
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
        setProductionEnabled(enableProduction);
    }

    /** Enable/disable the production sibling while its scheduling row retains learned state. */
    public void setProductionEnabled(boolean enabled) {
        productionEnabled = enabled;
    }
}
