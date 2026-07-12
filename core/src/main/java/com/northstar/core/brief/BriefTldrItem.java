package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;

public record BriefTldrItem(
        @NotBlank String storyId,
        @NotBlank String topic,
        @NotBlank String slug,
        @NotBlank String text) {
}
