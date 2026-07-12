package com.northstar.core.study;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores per-deck defaults without changing existing vocabulary items. */
@Service
public class VocabDeckService {

    private final VocabDeckPreferenceRepository preferences;

    VocabDeckService(VocabDeckPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public VocabDeckSettings settings(VocabLanguage language, String deck) {
        VocabLanguage requiredLanguage = Objects.requireNonNull(language, "language is required");
        String requiredDeck = displayDeck(deck);
        boolean enabled = preferences.findByLanguageAndDeckIgnoreCase(requiredLanguage, requiredDeck)
                .map(VocabDeckPreference::isProductionDefault)
                .orElse(false);
        return new VocabDeckSettings(requiredLanguage, requiredDeck, enabled);
    }

    @Transactional
    public VocabDeckSettings update(VocabLanguage language, String deck, boolean productionDefault) {
        VocabLanguage requiredLanguage = Objects.requireNonNull(language, "language is required");
        String requiredDeck = displayDeck(deck);
        VocabDeckPreference preference = preferences
                .findByLanguageAndDeckIgnoreCase(requiredLanguage, requiredDeck)
                .orElseGet(() -> new VocabDeckPreference(UUID.randomUUID(), requiredLanguage,
                        requiredDeck, productionDefault));
        preference.apply(productionDefault);
        preferences.save(preference);
        return new VocabDeckSettings(requiredLanguage, preference.getDeck(), productionDefault);
    }

    @Transactional(readOnly = true)
    public boolean productionDefault(VocabLanguage language, String deck) {
        return settings(language, deck).productionDefault();
    }

    static String displayDeck(String value) {
        if (value == null || value.isBlank() || value.strip().equalsIgnoreCase("General")) {
            return "General";
        }
        String deck = value.strip();
        if (deck.codePointCount(0, deck.length()) > 80) {
            throw new IllegalArgumentException("deck must be at most 80 characters");
        }
        return deck;
    }
}

