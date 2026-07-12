package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record BriefStory(
        @NotBlank String id,
        @NotBlank String topic,
        @NotBlank String slug,
        @NotBlank String title,
        @NotNull Instant publishedAt,
        int rank,
        double score,
        int tweetCount,
        int sourceCount,
        boolean fresh,
        boolean update,
        boolean superseded,
        @NotNull List<BriefTag> tags) {
}
