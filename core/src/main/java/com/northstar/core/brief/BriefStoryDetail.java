package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BriefStoryDetail(
        @NotNull BriefStory story,
        @NotBlank String summary,
        @NotNull List<BriefEntity> entities,
        BriefPreviousStory previousStory,
        @NotNull List<BriefSource> sources) {
}
