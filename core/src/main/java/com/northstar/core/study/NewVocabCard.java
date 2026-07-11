package com.northstar.core.study;

import java.util.UUID;

/**
 * Input for one new card. {@code metadata} is a JSON string (reading/example/
 * audio — whatever the capturing surface knows); null when there is none.
 */
public record NewVocabCard(String front, String back, String metadata, UUID disciplineId) {
}
