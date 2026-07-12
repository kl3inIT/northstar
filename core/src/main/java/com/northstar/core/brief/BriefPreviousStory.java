package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record BriefPreviousStory(
        @NotBlank String id,
        @NotBlank String topic,
        @NotBlank String slug,
        @NotBlank String title,
        @NotNull Instant publishedAt,
        int tweetCount,
        int sourceCount) {
}
