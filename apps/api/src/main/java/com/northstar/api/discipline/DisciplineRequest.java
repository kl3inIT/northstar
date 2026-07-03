package com.northstar.api.discipline;

import com.northstar.core.shared.ColorName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create payload for a discipline. Name size mirrors the V1 column width. */
record DisciplineRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull ColorName color) {
}
