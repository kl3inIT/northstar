package com.northstar.core.brief;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record BriefDay(
        @NotNull LocalDate date,
        @NotNull List<BriefTopicCount> topics,
        @NotNull List<BriefStory> stories) {
}
