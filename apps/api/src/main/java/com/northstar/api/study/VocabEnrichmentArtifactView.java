package com.northstar.api.study;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record VocabEnrichmentArtifactView(
        @NotNull UUID id,
        @NotNull String url,
        @NotNull String mediaType,
        int size) {
}
