package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;

public record BriefEntity(@NotBlank String text, @NotBlank String type) {
}
