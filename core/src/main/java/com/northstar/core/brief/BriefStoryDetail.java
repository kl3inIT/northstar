package com.northstar.core.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record BriefStoryDetail(
        @NotNull BriefStory story,
        @NotBlank String summary,
        @NotNull List<BriefEntity> entities,
        @Nullable @JsonInclude(JsonInclude.Include.NON_NULL) BriefPreviousStory previousStory,
        @NotNull List<BriefSource> sources) {
}
