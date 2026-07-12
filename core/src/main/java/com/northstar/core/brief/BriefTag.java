package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;

public record BriefTag(@NotBlank String name, @NotBlank String slug) {
}
