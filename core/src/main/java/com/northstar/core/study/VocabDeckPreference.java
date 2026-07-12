package com.northstar.core.study;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "vocab_deck_preference")
class VocabDeckPreference extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VocabLanguage language;

    @Column(nullable = false, length = 80)
    private String deck;

    @Column(name = "production_default", nullable = false)
    private boolean productionDefault;

    protected VocabDeckPreference() {
        // for JPA
    }

    VocabDeckPreference(UUID id, VocabLanguage language, String deck, boolean productionDefault) {
        super(id);
        this.language = language;
        this.deck = deck;
        this.productionDefault = productionDefault;
    }

    VocabLanguage getLanguage() {
        return language;
    }

    String getDeck() {
        return deck;
    }

    boolean isProductionDefault() {
        return productionDefault;
    }

    void apply(boolean value) {
        productionDefault = value;
    }
}

