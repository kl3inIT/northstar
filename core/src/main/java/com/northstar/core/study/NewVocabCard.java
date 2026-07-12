package com.northstar.core.study;

import java.util.UUID;

/**
 * Input for one new card. {@code metadata} is a JSON string (reading/example/
 * audio — whatever the capturing surface knows); null when there is none.
 */
public record NewVocabCard(String front, String back, String metadata,
        VocabLanguage language, String deck, UUID disciplineId, Boolean productionEnabled) {

    public NewVocabCard(String front, String back, String metadata,
            VocabLanguage language, String deck, UUID disciplineId) {
        this(front, back, metadata, language, deck, disciplineId, null);
    }
}
