package com.northstar.api.project;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

/** Body of POST/PUT project — full replace, like the other write endpoints. */
record ProjectRequest(
        @NotBlank String name,
        String notes,
        UUID disciplineId,
        LocalDate startDate,
        LocalDate targetDate) {
}
