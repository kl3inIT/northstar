package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** One normalized snapshot of a provider-owned ranked news feed. */
public record BriefFeed(
        @NotBlank String provider,
        @NotNull Instant updatedAt,
        boolean stale,
        @NotNull List<BriefTldrItem> tldr,
        @NotNull List<BriefDay> days) {
}
