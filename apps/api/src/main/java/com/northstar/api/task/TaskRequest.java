package com.northstar.api.task;

import java.time.LocalDate;
import java.time.LocalTime;

/** Create/update payload for a task. */
record TaskRequest(String title, String notes, LocalDate dueDate, LocalTime dueTime) {
}
