package com.northstar.api.project;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** Body for adding/editing one project milestone. */
record MilestoneRequest(@NotBlank String name, LocalDate dueDate) {
}
