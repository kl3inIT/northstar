package com.northstar.core.discipline;

import com.northstar.core.shared.ColorName;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Read model for discipline pickers and lists. */
public record DisciplineSummary(
        @NotNull UUID id,
        @NotNull String name,
        @NotNull ColorName color) {
}
