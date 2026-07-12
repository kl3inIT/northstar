package com.northstar.core.study;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Default review behavior for newly-created items in one language/deck. */
public record VocabDeckSettings(
        @NotNull VocabLanguage language,
        @NotNull String deck,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean productionDefault) {
}
